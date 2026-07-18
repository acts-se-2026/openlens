import base64
import os
import re
from concurrent.futures import ThreadPoolExecutor
from html.parser import HTMLParser
from urllib.parse import urljoin, urlparse

import requests
from dotenv import load_dotenv

from image_prep import image_preprocessing


load_dotenv()

OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
SEARCH_MODEL = "google/gemini-3-flash-preview"

PAGE_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 Chrome/126.0 Safari/537.36"
    ),
}

IGNORED_IMAGE_WORDS = {
    "avatar",
    "badge",
    "favicon",
    "icon",
    "logo",
    "placeholder",
    "profile",
    "sprite",
}


class PageImageParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.metadata_images = []
        self.page_images = []

    def handle_starttag(self, tag, attributes):
        attributes = dict(attributes)
        tag = tag.lower()

        if tag == "meta":
            property_name = (
                attributes.get("property")
                or attributes.get("name")
                or ""
            ).lower()
            content = attributes.get("content")

            if property_name in {
                "og:image",
                "og:image:url",
                "twitter:image",
                "twitter:image:src",
            } and content:
                self.metadata_images.append(content)

        if tag == "link":
            relation = attributes.get("rel", "").lower()
            href = attributes.get("href")
            if relation == "image_src" and href:
                self.metadata_images.append(href)

        if tag == "img":
            source = (
                attributes.get("data-src")
                or attributes.get("data-lazy-src")
                or attributes.get("src")
            )
            if not source:
                return

            searchable_text = " ".join(
                [
                    source,
                    attributes.get("alt", ""),
                    attributes.get("class", ""),
                    attributes.get("id", ""),
                ]
            ).lower()

            if any(word in searchable_text for word in IGNORED_IMAGE_WORDS):
                return

            width = parse_size(attributes.get("width"))
            height = parse_size(attributes.get("height"))
            score = 0

            if width >= 300 and height >= 200:
                score += 3
            if attributes.get("alt"):
                score += 1
            if source.startswith("data:"):
                score -= 10

            self.page_images.append((score, source))

    def best_image(self):
        if self.metadata_images:
            return self.metadata_images[0]

        if not self.page_images:
            return None

        self.page_images.sort(key=lambda item: item[0], reverse=True)
        return self.page_images[0][1]


def parse_size(value):
    match = re.search(r"\d+", str(value or ""))
    return int(match.group()) if match else 0


def get_openrouter_headers():
    api_key = os.getenv("OPENROUTER_API_KEY")
    if not api_key:
        raise RuntimeError("OPENROUTER_API_KEY is not set.")

    return {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }


def clean_text(text):
    return re.sub(r"\s+", " ", text or "").strip()


def get_preview_image(page_url):
    if urlparse(page_url).scheme not in {"http", "https"}:
        return None

    try:
        response = requests.get(
            page_url,
            headers=PAGE_HEADERS,
            timeout=3.5,
            allow_redirects=True,
            stream=True,
        )
        response.raise_for_status()

        content_type = response.headers.get("Content-Type", "").lower()
        if content_type.startswith("image/"):
            return response.url
        if "text/html" not in content_type:
            return None

        html_bytes = bytearray()
        for chunk in response.iter_content(chunk_size=8192):
            html_bytes.extend(chunk)
            if len(html_bytes) >= 250_000:
                break

        parser = PageImageParser()
        parser.feed(
            html_bytes.decode(
                response.encoding or "utf-8",
                errors="replace",
            )
        )

        image_url = parser.best_image()
        if not image_url:
            return None

        image_url = urljoin(response.url, image_url)
        if urlparse(image_url).scheme not in {"http", "https"}:
            return None

        return image_url
    except (requests.RequestException, ValueError):
        return None


def extract_citations(message):
    citations = []
    seen_urls = set()

    for annotation in message.get("annotations", []):
        if annotation.get("type") != "url_citation":
            continue

        citation = annotation.get("url_citation", {})
        url = citation.get("url")

        if not url or url in seen_urls:
            continue
        if urlparse(url).scheme not in {"http", "https"}:
            continue

        seen_urls.add(url)
        citations.append(
            {
                "title": clean_text(citation.get("title")) or "Related page",
                "url": url,
                "description": clean_text(citation.get("content"))[:300],
            }
        )

    if citations:
        return citations

    for title, url in re.findall(
        r"\[([^\]]+)\]\((https?://[^)]+)\)",
        message.get("content", ""),
    ):
        if url in seen_urls:
            continue

        seen_urls.add(url)
        citations.append(
            {
                "title": clean_text(title) or "Related page",
                "url": url,
                "description": "",
            }
        )

    return citations


def search_related_content(image_bytes, count=4):
    search_count = min(max(count + 2, 6), 10)
    processed_image = image_preprocessing(image_bytes)
    image_b64 = base64.b64encode(processed_image).decode("utf-8")

    query_payload = {
        "model": SEARCH_MODEL,
        "messages": [
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": (
                            "Write one precise web image-search query of 5 to 12 "
                            "words for this image. Name the main visible subject "
                            "and its most distinctive visual attributes, such as "
                            "species, object type, color, material, shape, setting, "
                            "or readable text. Output only the query, with no "
                            "explanation, label, quotation marks, or punctuation."
                        ),
                    },
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": f"data:image/jpeg;base64,{image_b64}",
                        },
                    },
                ],
            }
        ],
        "temperature": 0,
        "max_tokens": 80,
    }

    query_response = requests.post(
        OPENROUTER_URL,
        headers=get_openrouter_headers(),
        json=query_payload,
        timeout=45,
    )
    query_response.raise_for_status()

    query = clean_text(
        query_response.json()["choices"][0]["message"]["content"]
    ).strip("`'\".,:; ")

    query = re.sub(
        r"^(search query|query)\s*:\s*",
        "",
        query,
        flags=re.IGNORECASE,
    )[:200]

    if not query:
        return {"query": "", "images": [], "links": []}

    search_payload = {
        "model": SEARCH_MODEL,
        "messages": [
            {
                "role": "user",
                "content": (
                    "Search the web for pages containing photographs visually "
                    f"similar to this exact query: {query}. "
                    f"Return up to {search_count} highly relevant pages and cite every "
                    "result. Prefer pages whose main image clearly shows the same "
                    "subject and visual attributes. Exclude generic search tools, "
                    "AI image-search services, social-media homepages, logos, "
                    "icons, and unrelated results."
                ),
            }
        ],
        "plugins": [
            {
                "id": "web",
                "engine": "exa",
                "max_results": search_count,
                "exclude_domains": [
                    "lensaiapp.com",
                    "pinterest.com",
                    "facebook.com",
                    "instagram.com",
                ],
            }
        ],
        "temperature": 0,
        "max_tokens": 400,
    }

    response = requests.post(
        OPENROUTER_URL,
        headers=get_openrouter_headers(),
        json=search_payload,
        timeout=60,
    )
    response.raise_for_status()

    message = response.json()["choices"][0]["message"]
    citations = extract_citations(message)[:search_count]

    related_links = [
        {
            "title": citation["title"],
            "url": citation["url"],
            "description": citation["description"],
        }
        for citation in citations[:count]
    ]

    if not citations:
        return {"query": query, "images": [], "links": []}

    with ThreadPoolExecutor(max_workers=search_count) as executor:
        preview_images = list(
            executor.map(
                get_preview_image,
                [citation["url"] for citation in citations],
            )
        )

    related_images = []
    seen_images = set()

    for citation, preview_image in zip(citations, preview_images):
        if not preview_image or preview_image in seen_images:
            continue

        seen_images.add(preview_image)
        related_images.append(
            {
                "title": citation["title"],
                "thumbnail_url": preview_image,
                "image_url": preview_image,
                "source_url": citation["url"],
            }
        )
        if len(related_images) == count:
            break

    return {
        "query": query,
        "images": related_images,
        "links": related_links,
    }
