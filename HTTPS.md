## HTTPS Setup & Connecting — open-lens.dev


## 1. Connect to the instance

```bash
cd Downloads
ssh user@89.169.102.74 -i cpu-nebius
```

Run the SSH command from the folder where the `cpu-nebius` key file actually lives (e.g. `Downloads`), otherwise SSH won't find it and you'll get a `Permission denied (publickey)` error.

## 2. Install system dependencies for Certbot

```bash
sudo apt update
sudo apt install -y python3 python3-dev python3-venv libaugeas-dev gcc
```

## 3. Remove any pre-installed Certbot package

If Certbot was previously installed via `apt`/`dnf`/`yum`, remove it first so the venv-installed version is the one used:

```bash
sudo apt-get remove certbot
```

## 4. Set up a Python virtual environment for Certbot

```bash
sudo python3 -m venv /opt/certbot/
sudo /opt/certbot/bin/pip install --upgrade pip
```

## 5. Install Certbot into the venv

```bash
sudo /opt/certbot/bin/pip install certbot
```

## 6. Make the `certbot` command globally available

```bash
sudo ln -s /opt/certbot/bin/certbot /usr/local/bin/certbot
```

## 7. Get the certificate

Since the server isn't running on port 80 at cert-issuance time, use the **standalone** plugin — Certbot temporarily spins up its own webserver on port 80 to complete validation. Make sure port 80 is open (firewall / security group) before running this.

```bash
sudo certbot certonly --standalone -d open-lens.dev
```

Expected output:

```
Certificate is saved at: /etc/letsencrypt/live/open-lens.dev/fullchain.pem
Key is saved at: /etc/letsencrypt/live/open-lens.dev/privkey.pem
This certificate expires on 2026-10-16.
```

> If you don't have an existing HTTP site running and don't want to briefly stop anything, `--standalone` is the right choice. If you already have a webserver bound to port 80 that must stay up, use `--webroot` instead (requires your server to serve `/.well-known/acme-challenge/` correctly).

## 8. Run the server with HTTPS directly (uvicorn)

Run this inside a `tmux` session so it survives SSH disconnects:

```bash
tmux new -s vllm
```

Then, inside the tmux session:

```bash
uv run --project server uvicorn server.server:app \
  --host 0.0.0.0 \
  --port 443 \
  --ssl-keyfile /etc/letsencrypt/live/open-lens.dev/privkey.pem \
  --ssl-certfile /etc/letsencrypt/live/open-lens.dev/fullchain.pem
```

Detach without killing it: `Ctrl+B`, then `D`.
Reattach later: `tmux attach -t vllm`.

> Binding to port `443` and reading `privkey.pem` both require root — either run the whole command with `sudo`, or grant `CAP_NET_BIND_SERVICE` to the interpreter and add your user to the `ssl-cert` group.

## 9. Verify

```bash
curl https://open-lens.dev/
```

## 10. Renewal

Certbot certs last 90 days. Set up auto-renewal:

```bash
sudo certbot renew --dry-run
```

Since uvicorn only reads the cert files at startup, add a renewal deploy-hook to restart the server whenever the cert renews:

```bash
sudo mkdir -p /etc/letsencrypt/renewal-hooks/deploy
sudo nano /etc/letsencrypt/renewal-hooks/deploy/restart-server.sh
```

```bash
#!/bin/bash
tmux send-keys -t vllm C-c
tmux send-keys -t vllm "uv run --project server uvicorn server.server:app --host 0.0.0.0 --port 443 --ssl-keyfile /etc/letsencrypt/live/open-lens.dev/privkey.pem --ssl-certfile /etc/letsencrypt/live/open-lens.dev/fullchain.pem" Enter
```

```bash
sudo chmod +x /etc/letsencrypt/renewal-hooks/deploy/restart-server.sh
```

> A systemd service is generally more reliable than restarting inside `tmux` via a hook script — worth switching to if this becomes a permanent setup.

## Quick Reference

| Task | Command |
|---|---|
| SSH into instance | `ssh user@89.169.96.173 -i cpu-nebius` |
| Get cert (standalone) | `sudo certbot certonly --standalone -d open-lens.dev` |
| Test renewal | `sudo certbot renew --dry-run` |
| New tmux session | `tmux new -s vllm` |
| Detach from tmux | `Ctrl+B`, then `D` |
| Reattach to tmux | `tmux attach -t vllm` |
| Run server over HTTPS | `uv run --project server uvicorn server.server:app --host 0.0.0.0 --port 443 --ssl-keyfile ... --ssl-certfile ...` |
