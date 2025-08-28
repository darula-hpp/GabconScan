# 📱 Gabcon Scan  

An Android barcode scanner app built with **CameraX** and **Google ML Kit** that scans barcodes, plays a confirmation beep, and sends the scanned result to a configurable server endpoint.  

---

## 🚀 Features  
- 🔍 **Real-time barcode scanning** using CameraX + ML Kit  
- 🎵 **Beep sound feedback** after successful scan  
- 🌐 **Sends scanned data to a backend server** via HTTP POST (using OkHttp)  
- ⚙️ **Hidden settings panel** (tap preview 5 times) to configure:  
  - Server IP / Domain  
  - Port  
- 💾 **Persistent settings** stored in `SharedPreferences`  

---

## 🛠️ Tech Stack  
- **Kotlin** (Android)  
- **CameraX** for camera preview  
- **Google ML Kit Barcode Scanning** for barcode detection  
- **OkHttp** for HTTP requests  
- **AndroidX** + **Material Components**  

---

## 📸 Usage  

1. Launch the app → The camera preview starts automatically.  
2. Point the camera at a barcode → It will scan and play a beep sound.  
3. The scanned data is sent to your configured server endpoint. 
