FROM public.ecr.aws/lambda/python:3.14

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# handler.py imports the providers package, and each provider imports the shared prompts.
# get_provider() imports its SDK inside the selected branch, so this image never loads
# google-genai and needs no Vertex dependency.
COPY handler.py prompts.py ${LAMBDA_TASK_ROOT}/
COPY providers/ ${LAMBDA_TASK_ROOT}/providers/

CMD ["handler.handler"]
