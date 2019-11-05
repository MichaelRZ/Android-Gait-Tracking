# Android-Gait-Tracking
Android app that tracks phone and watch acceleration and rotation for a given amount of time. Recording for the GPS location and watch microphone has been added. Made for the WISDM research lab at Fordham University.

Usage:

# Installation - 
  Open the project in Android Studio. Upload the app to the phone through Android Studio. Make sure the phone and watch versions are compatible first. Make sure debugging is enabled on the phone as well as the watch, and that Wear OS is enabled on both and that they are paired. To upload the app to the watch as well, use ADB in android studio as stated here, using either WIFI or Bluetooth:
https://developer.android.com/training/wearables/apps/debugging

Basically:
1. Connect phone to computer with a USB cable.
2. Connect watch to phone with the Wear OS app.
3. Turn on debugging for both.
4. Type in these lines in Android Studio's terminal, after installing ADB:
  adb forward tcp:4444 localabstract:/adb-hub
  adb connect 127.0.0.1:4444

After this, there shouldn't need to be a computer except to access the CSV / WAV files.

# Recording - 
Different recorders can be turned on and off through the phone app.

All recording is done through the watch app. In the watch app there is a set of buttons, spanning from 5 seconds to 15 minutes, that record the watch and phone data for that set amount of time. The screen shifts to the visual timer when started. There is also a start / stop option, which can record for even longer periods of time, and the only difference is you press the button again when you are finished. All recording has a one second gap between pressing the button and recording data, to account for the movement needed to press the button.

At the end of the recording period, the watch should vibrate as it did at the start, and there should be a little notification on the phone (the watch vibration should be enough, however). If paired correctly, there should be a numbered folder with CSV files saved under "data/data/com.example.wisdm/files", accessible from android studio's "Device File Explorer". This contains individual CSV files for both watch and phone rotation and acceleration, and phone GPS location.

Microphone recordings are saved on the watch and can be found under "/sdcard/music".
  
Recording is currently limited to around 20hz, or around 50 milliseconds between entries. This is either due to the libraries being used or hardware limitations, as there is no limit set in the software.
 
Questions can be emailed here: "mriadzaky [at] fordham.edu"
