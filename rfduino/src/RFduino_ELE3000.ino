#include <RFduinoBLE.h>

// Constants
const char DEVICE_NAME[] = "RFduino";
const char ADVERTISEMENT_DATA[] = "ECG1";
const int ADVERTISEMENT_INTERVAL = 250;
const int TX_POWER_LEVEL = 0;
const int BAUD_RATE = 9600;
const int SAMPLING_RATE = 300;
const int SETUP_TIME = 100;
const int ELECTRODE_PIN = 2;
const int SLEEP_TIME = 1;
const int SAMPLE_SIZE = 6; // 6 bytes
const int SAMPLES_PER_PACKET = 3; 

// Actions
const uint8_t STOP_SAMPLING = 0x00;
const uint8_t START_SAMPLING = 0x01;

// Attributes
bool isSampling;
long startSamplingTime;
int sampleNumber;

void setup()
{
  // Attributes Initialization
  isSampling = false;
  sampleNumber = 0;
  analogReadResolution(12);
  
  // Serial Initialization
  Serial.begin(BAUD_RATE);
  
  // Bluetooth Initialization
  RFduinoBLE.deviceName = DEVICE_NAME;
  RFduinoBLE.advertisementData = ADVERTISEMENT_DATA;
  RFduinoBLE.advertisementInterval = MILLISECONDS(ADVERTISEMENT_INTERVAL);
  RFduinoBLE.txPowerLevel = TX_POWER_LEVEL;  // (-20dBm to +4 dBm)
  RFduinoBLE.begin();
  
  delay(SETUP_TIME); // Time for set up
}

void loop()
{
  if(isSampling)
  {
    // Sample value from electrodes
    unsigned long sampleTime = millis() - startSamplingTime;
    unsigned int sampleValue = analogRead(ELECTRODE_PIN);

    // Send Data
    char data[SAMPLE_SIZE*SAMPLES_PER_PACKET]; 
    data[sampleNumber*SAMPLE_SIZE] = (char) ((sampleTime >> 24) & 0xFF);
    data[sampleNumber*SAMPLE_SIZE+1] = (char) ((sampleTime >> 16) & 0xFF);
    data[sampleNumber*SAMPLE_SIZE+2] = (char) ((sampleTime >> 8) & 0xFF);
    data[sampleNumber*SAMPLE_SIZE+3] = (char) ((sampleTime) & 0xFF);
    data[sampleNumber*SAMPLE_SIZE+4] = (char) (sampleValue >> 8);
    data[sampleNumber*SAMPLE_SIZE+5] = (char) (sampleValue % 256);
    sampleNumber++;
    
    if(sampleNumber == SAMPLES_PER_PACKET)
    { 
    while(RFduinoBLE.radioActive);
      {
         RFduinoBLE.send(data, SAMPLE_SIZE*SAMPLES_PER_PACKET);
      }
      sampleNumber = 0;   
    }

    // Wait until next sample
    RFduino_ULPDelay(SECONDS(1.0/SAMPLING_RATE));    
  }
  else
  {
    RFduino_ULPDelay(SECONDS(SLEEP_TIME)); // Wait for BLE interrupt.
  }
}

void RFduinoBLE_onAdvertisement(bool start)
{
  Serial.println("Advertising..."); // For debugging purpose.
}

void RFduinoBLE_onConnect()
{
  Serial.println("Connected to Radio..."); // For debugging purpose.
}

void RFduinoBLE_onDisconnect()
{
  Serial.println("Disconnected from Radio..."); // For debugging purpose.
  isSampling = false;
  sampleNumber = 0;  
}

void RFduinoBLE_onReceive(char *data, int len)
{
  
  if(len > 0 )
  {
    uint8_t action = data[0];
    if(action == START_SAMPLING)
    {
      Serial.println("Turning sampling on"); // For debugging purpose.
      
      isSampling = true;
      startSamplingTime = millis();
      sampleNumber = 0;  
    }
    else if(action == STOP_SAMPLING)
    {
      Serial.println("Turning sampling off"); // For debugging purpose.
      sampleNumber = 0;  
      isSampling = false;
    }
  }
}
