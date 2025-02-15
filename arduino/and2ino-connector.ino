#define ANALOG  0
#define DIGITAL 1

#define CHANGE  0
#define READ    1
#define WRITE   2

void setup() {
  Serial.begin(9600);
}

void loop() {
  
  if (Serial.available() > 0) {

    char payload = Serial.read();

    char command = (payload & 0xC0) >> 6;
    char type = (payload & 0x20) >> 5;
    
    int pin, value;

    if (type == ANALOG) {
      pin = ((payload & 0x1C) >> 2) + 14;
      value = payload & 0x3;
    } else {
      pin = (payload & 0x1E) >> 1;
      value = payload & 0x1;
    }

    switch (command) {
      case CHANGE: {
        pinMode(pin, value);
        Serial.write(1);
        break;
      }
      case READ: {
        if (type == ANALOG) {
          value = analogRead(pin);
          Serial.write((value >> 8) & 0xFF);
          Serial.write(value & 0xFF);
        } else {
          Serial.write(digitalRead(pin));
        }
        break;
      }
      case WRITE: {
        if (type == ANALOG) {
          while (Serial.available() < 1);
          value = (value << 8) | Serial.read();
          analogWrite(pin, value);
        } else {
          digitalWrite(pin, value);
        }
        Serial.write(1);
        break;
      }
    }
  }
}
