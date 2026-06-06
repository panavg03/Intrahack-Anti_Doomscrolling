#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <Wire.h>

// BLE UUIDs
#define SERVICE_UUID           "12345678-1234-5678-1234-56789abcdef0"
#define ACCEL_CHAR_UUID        "87654321-4321-8765-4321-210987654321"
#define SHOCK_CMD_CHAR_UUID    "11111111-2222-3333-4444-555555555555"

// Haptic feedback pin
#define HAPTIC_PIN 25
#define HAPTIC_CHANNEL 0
#define PWM_FREQ 1000

// BLE references
BLECharacteristic* accelCharacteristic;
BLECharacteristic* shockCharacteristic;
BLEServer* pServer;
bool deviceConnected = false;

// Shock command structure
struct ShockCommand {
  uint16_t duration_ms;
  uint8_t intensity;
} __attribute__((packed));

// BLE Server callbacks
class MyServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) override {
    deviceConnected = true;
    Serial.println("[BLE] Client connected");
    delay(500);
  }
  void onDisconnect(BLEServer* pServer) override {
    deviceConnected = false;
    Serial.println("[BLE] Client disconnected");
    pServer->getAdvertising()->start();
  }
};

// Shock command callback
class ShockCommandCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* pCharacteristic) override {
    std::string value = pCharacteristic->getValue();
    if (value.length() != sizeof(ShockCommand)) {
      Serial.println("[ERROR] Invalid shock command size");
      return;
    }
    
    ShockCommand* cmd = (ShockCommand*)value.c_str();
    Serial.printf("[SHOCK] Duration: %d ms, Intensity: %d/255\n", cmd->duration_ms, cmd->intensity);
    
    uint8_t pwmDuty = constrain(cmd->intensity, 0, 255);
    ledcWrite(HAPTIC_CHANNEL, pwmDuty);
    delay(cmd->duration_ms);
    ledcWrite(HAPTIC_CHANNEL, 0);
  }
};

// Initialize BLE
void initBLE() {
  BLEDevice::init("DoomStop-Ring");
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());
  
  BLEService* pService = pServer->createService(SERVICE_UUID);
  
  accelCharacteristic = pService->createCharacteristic(
    ACCEL_CHAR_UUID,
    BLECharacteristic::PROPERTY_NOTIFY
  );
  accelCharacteristic->addDescriptor(new BLE2902());
  
  shockCharacteristic = pService->createCharacteristic(
    SHOCK_CMD_CHAR_UUID,
    BLECharacteristic::PROPERTY_WRITE
  );
  shockCharacteristic->setCallbacks(new ShockCommandCallbacks());
  
  pService->start();
  
  BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMaxPreferred(0x12);
  BLEDevice::startAdvertising();
  
  Serial.println("[BLE] Server initialized and advertising");
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  
  Serial.println("\n\n=== DoomStop Ring - ESP32 BLE Server ===");
  
  // Setup haptic PWM
  ledcSetup(HAPTIC_CHANNEL, PWM_FREQ, 8);
  ledcAttachPin(HAPTIC_PIN, HAPTIC_CHANNEL);
  ledcWrite(HAPTIC_CHANNEL, 0);
  
  initBLE();
  
  Serial.println("[SYSTEM] Ready to connect");
}

void loop() {
  delay(100);
}