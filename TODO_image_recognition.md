# TODO: Image Recognition (import puzzle from picture)

This feature allows a user to import a specific puzzle from a picture rather than use a generated one

## Approach

To build a highly cost-effective solution for converting Sudoku images to text on AWS, the best approach is to avoid paying per-image for managed ML services (like Amazon Textract or Rekognition) and instead deploy a **Serverless Custom Computer Vision pipeline**. 

By using AWS Lambda paired with open-source libraries (OpenCV and a lightweight OCR model), your compute costs will be incredibly close to zero, easily fitting into the AWS Free Tier for low-to-medium traffic.

Here is a breakdown of how to architect and build this solution.

---

### Phase 1: The Computer Vision Pipeline (OpenCV)
Before you can read the numbers, you need to isolate the Sudoku grid from the rest of the image. You can do this using standard Python and OpenCV.

1. **Preprocessing:** Convert the uploaded image to grayscale and apply a Gaussian blur to reduce noise. Then, use Adaptive Thresholding to make the image pure black and white.
2. **Grid Detection:** Use OpenCV's `findContours` to identify the largest four-sided polygon in the image. This is almost always the outer boundary of the Sudoku puzzle.
3. **Perspective Transform:** Users rarely take perfectly flat photos. Apply a perspective transform (a "warp") to stretch the detected grid into a perfect, top-down square. 
4. **Cell Extraction:** Divide the perfectly square image into an exact 9x9 grid, resulting in 81 individual cell images. 



### Phase 2: Digit Recognition (OCR)
Once you have the 81 individual cells, you need to extract the numbers (or determine if a cell is blank).

* **The extremely cheap/fast way:** Use **Tesseract OCR**. You can configure it specifically to look for a single digit (0-9) by setting the Page Segmentation Mode (PSM) to 10 (treat image as a single character). 
* **The highly accurate way:** Train or download a pre-trained lightweight Convolutional Neural Network (CNN) based on the MNIST dataset. Since you are only recognizing 9 standardized digits, a tiny model (under 2MB) built with PyTorch or TensorFlow Lite will execute in milliseconds.

### Phase 3: Cost-Effective AWS Architecture
To keep costs as low as possible, we will use a serverless architecture. 



1. **Amazon API Gateway:** This acts as the front door for your app. Your app sends a POST request containing the image to this API.
2. **AWS Lambda:** This is where your OpenCV and OCR Python code will run. Lambda charges only for the exact milliseconds your code executes. 
3. **Amazon S3 (Optional):** If your images are large, you can upload them to S3 first and pass the S3 URL to Lambda. If the images are small and heavily compressed by your app beforehand, you can simply send them as base64-encoded strings directly through API Gateway, saving you S3 costs and latency.

**Why this is cost-effective:**
* **AWS Lambda:** The Free Tier includes **1 million free requests** and 400,000 GB-seconds of compute time per month. Even after that, it is roughly $0.20 per 1 million requests.
* **Amazon API Gateway:** The Free Tier includes **1 million API calls** per month for the first 12 months. 
* Contrast this with **Amazon Textract**, which costs $1.50 per 1,000 pages. For a Sudoku app with heavy usage, Textract's costs would quickly snowball, whereas Lambda keeps you in the pennies.

### Deployment Tip: Lambda Container Images
Because libraries like OpenCV and Tesseract/PyTorch can be large, they often exceed the standard 250MB deployment package limit for AWS Lambda. The best workaround is to package your code and dependencies into a **Docker Container Image** and deploy that to Lambda. AWS allows Lambda container images up to 10GB in size, which completely resolves any library bloat issues.

---

Would you like me to draft the core OpenCV Python script for extracting the 81 cells from the puzzle?
