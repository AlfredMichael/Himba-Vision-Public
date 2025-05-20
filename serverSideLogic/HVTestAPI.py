import requests

# URL of the Flask API
url = "https://BunchOfNumbers.ngrok-free.app/navigate"

# Path to the image you want to test
image_path = 'png/distance.jpg'

# Focal length in pixels
focal_length_px = 1000.0  # Replace with your actual focal length in pixels

# Read the image file
with open(image_path, 'rb') as image_file:
    # Prepare the payload
    files = {'image': image_file}
    data = {'focal_length_px': focal_length_px}
    headers = {'ngrok-skip-browser-warning': 'true'}

    # Send the POST request
    response = requests.post(url, files=files, data=data, headers=headers)

# Check for a successful response
if response.status_code == 200:
    response_data = response.json()

    # Extract navigation instructions
    navigation = response_data.get('navigation', {})
    minimal_nav = navigation.get('minimal_navigation', [])
    maximal_nav = navigation.get('maximal_navigation', [])

    # Extract detailed object results
    object_results = response_data.get('object_results', [])

    # Display Minimal Navigation Instructions
    print("=== Minimal Navigation Instructions ===")
    for cmd in minimal_nav:
        print(f"- {cmd}")

    # Display Maximal Navigation Instructions
    print("\n=== Maximal Navigation Instructions ===")
    for cmd in maximal_nav:
        print(f"- {cmd}")

    # Display Detailed Object Results
    print("\n=== Object Detection Results ===")
    for obj in object_results:
        print(f"- {obj}")
else:
    print(f"Error {response.status_code}: {response.text}")