ECG Monitor
==============

Description
--------------

This project was part of the class ELE3000. It consists of a microcontroller (RFduino) that records a person's ECG using a custom-made acquisition circuit. This ECG is then sent wirelessly to an Android smartphone using Bluetooth Low Energy. The Android smartphone can then display the ECG in real-time using an application (which depends on the androidplot library). 

Such a device would allow at-risk individuals to have their ECG monitored continuously. In case of a heart attack, the smartphone could automatically alert the authorities.

Structure
--------------
- **android**: contains the code for the Android application
- **rfduino**: contains the code for the RFduino microcontroller
- **doc**: contains a poster and a report regarding the project (in French)

Misc
--------------

This project is still in a draft state. It is not documented nor thoroughly tested. Therefore, it might prove difficult to use without modification.

