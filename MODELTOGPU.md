# How to Turn a Model On (GPU Server)
 
## Prerequisites
 
- Choose the model (from HuggingFace)
- Have the GPU server IP address
- Have the SSH key file (e.g. `cpu-nebius`)
---
 
## 1. Connect to the GPU server
 
```bash
cd Downloads   # or wherever your SSH key file is located
ssh user@89.169.96.173 -i cpu-nebius
```
 
## 2. Activate the environment
 
```bash
source .venv/bin/activate
```
 
## 3. Verify vLLM is installed
 
```bash
python -c "import vllm; print(vllm.__version__)"
```
 
Should print a version number (e.g. `0.25.1`). If this fails, vLLM needs to be (re)installed:
 
```bash
uv venv --python 3.12 --seed
source .venv/bin/activate
uv pip install vllm --torch-backend=auto
```
 
**Not necessary if vLLM already works:**
 
```bash
uv run --with vllm vllm --help
```
 
## 4. Start a tmux session
 
Running inside tmux keeps the server alive even if the SSH connection drops or the terminal window is closed.
 
```bash
tmux new -s vllm
source .venv/bin/activate   # re-activate inside the new tmux session
```
 
## 5. Start vLLM serving the model
 
Replace the model name with the one you chose:
 
```bash
vllm serve RedHatAI/gemma-4-31B-it-FP8-blockit \
  --quantization fp8_per_tensor \
  --kv-cache-dtype fp8 \
  --max-model-len 4096
```
 
- `--quantization` / `--kv-cache-dtype` — only needed for quantized models like this one (skip for regular fp16/bf16 models)
- `--max-model-len` — caps context length; lower it if you hit out-of-memory errors
Wait for a line confirming the server is running (`Uvicorn running on ...`). This means the model is loaded into GPU memory and the server is ready.
 
## 6. Detach from tmux (server keeps running)
 
Press: `Ctrl+B`, release, then `D`
 
Reattach later with:
 
```bash
tmux attach -t vllm
```
 
## 7. Test the server
 
```bash
curl http://localhost:8000/v1/models
```
