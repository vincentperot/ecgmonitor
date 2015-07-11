// Constants
const int BAUD_RATE = 115200;
const int SETUP_TIME = 100;
const int SAMPLING_RATE = 350;

// Attributes
long start;

void setup()
{
  // Serial Initialization
  Serial.begin(BAUD_RATE);
  analogReadResolution(12);
  tone(6,150);
  Serial.println("Time (ms), Voltage (V)");  
  start = millis();
  delay(SETUP_TIME); // Time for set up

}

void loop()
{
   long time = millis() - start;
   int value = analogRead(2);
   float voltage = value*3.0/(4096.0);
   Serial.print(time);
   Serial.print(", ");
   Serial.println(voltage);
   RFduino_ULPDelay(SECONDS(1.0/SAMPLING_RATE)); 
}
