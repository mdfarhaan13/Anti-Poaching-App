## Installation Guide

This guide will help you set up and run the **AI-Based Anti-Poaching Detection System** on your local machine.

---

### Prerequisites

Make sure the following are installed on your system:

- Python **3.8 or higher**
- Git
- pip (Python package manager)

#### Verify installation:

python --version
pip --version
git --version


#### Step 1: Clone the Repository

git clone https://github.com/mdfarhaan13/Anti-Poaching-App.git
cd Anti-Poaching-App

#### Create Virtual Environment (Recommended)

python -m venv venv
venv\Scripts\activate

#### Step 3: Install Dependencies

pip install ultralytics opencv-python

#### Setup YOLO Model
Place your trained model file (e.g., best.pt) inside the project directory
Ensure the model path is correctly set in the code:
model = YOLO("best.pt")

#### Step 6: Run the Application

python main.py
