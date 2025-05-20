# Project README

## Overview
This project is designed to run on an actual Android device and utilizes the phone’s camera for core functionality. As a result, it **will not work on an emulator**.

## Installation & Running the App
1. Load the project in **Android Studio**.
2. Connect an **actual Android device** for deployment.
3. Run the application.

### Troubleshooting
- If you encounter difficulties running the app, search for the **APK file** in the `build` directory and install it manually on your device.

## Panoptic Segmentation Setup
To enable **panoptic segmentation**, follow these steps:
1. Create a **premium Ngrok account**.
2. Load the **Jupyter Notebook** found in the server-side logic.
3. Replace the existing **Ngrok key** with your **premium Ngrok key**.
4. This will expose the **Kaggle/Colab notebook hardware resources** to the app for processing.

## Notes
The database structure can be fond in the database folder, but you dont have to worry about this as it would be hosted on firebase for a year, the database would be deleted in 2027
if you are reading this in 2027, you would have to create the firebase database, storage and authentication structure to run the project.

## Notes
- Ensure you have the necessary dependencies installed in your environment.
- A stable internet connection is required for seamless communication between the app and cloud resources for panoptic segmentation.