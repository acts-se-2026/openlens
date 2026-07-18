import os
import re
from concurrent.futures import ThreadPoolExecutor
from html.parser import HTMLParser
from urllib.parse import urljoin, urlparse
import base64

import requests
from dotenv import load_dotenv


load_dotenv()

OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
SEARCH_MODEL = "google/gemini-3-flash-preview"

PAGE_HEADERS = {
    "User-Agent": "OpenLens/1.0 student project",
}


class OpenGraphParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.images = {}

    def handle_starttag(self, tag, attributes):
        if tag.lower() != "meta":
            return

        attributes = dict(attributes)

        property_name = (
            attributes.get("property")
            or attributes.get("name")
            or ""
        ).lower()

        content = attributes.get("content")

        if property_name in {
            "og:image",
            "twitter:image",
            "twitter:image:src",
        } and content:
            self.images[property_name] = content

    def get_image(self):
        return (
            self.images.get("og:image")
            or self.images.get("twitter:image")
            or self.images.get("twitter:image:src")
        )


def get_openrouter_headers():
    api_key = os.getenv("OPENROUTER_API_KEY")

    if not api_key:
        raise RuntimeError("OPENROUTER_API_KEY is not set.")

    return {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }


def clean_text(text):
    text = re.sub(r"\s+", " ", text or "")
    return text.strip()


def get_preview_image(page_url):
    parsed_url = urlparse(page_url)

    if parsed_url.scheme not in {"http", "https"}:
        return None

    try:
        response = requests.get(
            page_url,
            headers=PAGE_HEADERS,
            timeout=2.5,
            allow_redirects=True,
            stream=True,
        )

        response.raise_for_status()

        content_type = response.headers.get(
            "Content-Type",
            "",
        ).lower()

        if "text/html" not in content_type:
            return None

        html_bytes = bytearray()

        for chunk in response.iter_content(chunk_size=8192):
            html_bytes.extend(chunk)

            if len(html_bytes) >= 150_000:
                break

        encoding = response.encoding or "utf-8"
        html = html_bytes.decode(encoding, errors="replace")

        parser = OpenGraphParser()
        parser.feed(html)

        image_url = parser.get_image()

        if not image_url:
            return None

        image_url = urljoin(response.url, image_url)

        if urlparse(image_url).scheme not in {"http", "https"}:
            return None

        return image_url

    except requests.RequestException:
        return None


def extract_citations(message):
    citations = []
    seen_urls = set()

    for annotation in message.get("annotations", []):
        if annotation.get("type") != "url_citation":
            continue

        citation = annotation.get("url_citation", {})

        url = citation.get("url")
        title = citation.get("title") or "Related page"
        description = clean_text(citation.get("content", ""))

        if not url or url in seen_urls:
            continue

        if urlparse(url).scheme not in {"http", "https"}:
            continue

        seen_urls.add(url)

        citations.append({
            "title": title,
            "url": url,
            "description": description[:300],
        })

    if citations:
        return citations

    content = message.get("content", "")

    markdown_links = re.findall(
        r"\[([^\]]+)\]\((https?://[^)]+)\)",
        content,
    )

    for title, url in markdown_links:
        if url in seen_urls:
            continue

        seen_urls.add(url)

        citations.append({
            "title": title,
            "url": url,
            "description": "",
        })

    return citations


def search_related_content(image_bytes, count=4):
    image_b64 = base64.b64encode(
        image_bytes
    ).decode("utf-8")

    payload = {
        "model": SEARCH_MODEL,
        "messages": [
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": (
                            "Analyze the uploaded image and search "
                            "the web for useful pages closely related "
                            "to its main visual subject. "
                            f"Find up to {count} relevant results. "
                            "Prefer trustworthy and informative "
                            "sources and cite every result."
                        ),
                    },
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": (
                                "data:image/jpeg;base64,"
                                + image_b64
                            )
                        },
                    },
                ],
            }
        ],
        "tools": [
            {
                "type": "openrouter:web_search",
                "parameters": {
                    "engine": "exa",
                    "max_results": count,
                    "max_total_results": count,
                },
            }
        ],
    }

    response = requests.post(
        OPENROUTER_URL,
        headers=get_openrouter_headers(),
        json=payload,
        timeout=60,
    )

    response.raise_for_status()

    message = response.json()["choices"][0]["message"]
    citations = extract_citations(message)[:count]

    related_links = [
        {
            "title": citation["title"],
            "url": citation["url"],
            "description": citation["description"],
        }
        for citation in citations
    ]

    if not citations:
        return {
            "images": [],
            "links": [],
        }

    with ThreadPoolExecutor(
        max_workers=count
    ) as executor:
        preview_images = list(
            executor.map(
                get_preview_image,
                [
                    citation["url"]
                    for citation in citations
                ],
            )
        )

    related_images = []
    seen_images = set()

    for citation, preview_image in zip(
        citations,
        preview_images,
    ):
        if not preview_image:
            continue

        if preview_image in seen_images:
            continue

        seen_images.add(preview_image)

        related_images.append({
            "title": citation["title"],
            "thumbnail_url": preview_image,
            "image_url": preview_image,
            "source_url": citation["url"],
        })

    return {
        "images": related_images,
        "links": related_links,
    }