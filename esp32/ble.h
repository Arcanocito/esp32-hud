#ifndef BLE_H
#define BLE_H

#include <BLE2902.h>
#include <BLEDescriptor.h>
#include <BLEDevice.h>
#include <BLESecurity.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <string>
#include <vector>

BLEServer* pServer      = NULL;
bool deviceConnected    = false;
bool oldDeviceConnected = false;

#define SERVICE_UUID          "ec91d7ab-e87c-48d5-adfa-cc4b2951298a"
#define CHA_SETTINGS          "9d37a346-63d3-4df6-8eee-f0242949f59f"
#define CHA_NAV               "0b11deef-1563-447f-aece-d3dfeb1c1f20"
#define CHA_NAV_TBT_ICON      "d4d8fcca-16b2-4b8e-8ed5-90137c44a8ad"
#define CHA_NAV_TBT_ICON_DESC "d63a466e-5271-4a5d-a942-a34ccdb013d9"
#define CHA_GPS_SPEED         "98b6073a-5cf3-4e73-b6d3-f8e05fa018a9"

void onCharacteristicWrite(const String& uuid, uint8_t* data, size_t length);
void onConnectionChange(bool connected);

struct CharacteristicConfig {
    String name;
    String uuid;
    BLECharacteristic* bleCharacteristic;

    CharacteristicConfig(const char* characteristicName, const char* characteristicUuid)
    : name(characteristicName), uuid(characteristicUuid), bleCharacteristic(nullptr) {
    }
};

struct ServiceConfig {
    String name;
    String uuid;
    BLEService* bleService;
    std::vector<CharacteristicConfig> characteristics;

    ServiceConfig(const char* serviceName, const char* serviceUuid)
    : name(serviceName), uuid(serviceUuid), bleService(nullptr), characteristics() {
    }

    CharacteristicConfig* findCharacteristicByUuid(const String& wantedUuid) {
        for (auto& item : characteristics) {
            if (item.uuid == wantedUuid) {
                return &item;
            }
        }
        return nullptr;
    }
};

struct MyBleServer {
    BLEServer* bleServer = nullptr;
    std::vector<ServiceConfig> services;

    ServiceConfig* findServiceByUuid(const String& wantedUuid) {
        for (auto& item : services) {
            if (item.uuid == wantedUuid) {
                return &item;
            }
        }
        return nullptr;
    }

    CharacteristicConfig* findCharacteristicByUuid(const String& wantedUuid) {
        for (auto& item : services) {
            CharacteristicConfig* result = item.findCharacteristicByUuid(wantedUuid);
            if (result != nullptr) {
                return result;
            }
        }
        return nullptr;
    }
};

MyBleServer server;

class ServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) override {
        Serial.println("Device connected");
        deviceConnected = true;
        onConnectionChange(deviceConnected);
    }

    void onDisconnect(BLEServer* pServer) override {
        Serial.println("Device disconnected, start advertising...");
        deviceConnected = false;
        server.bleServer->startAdvertising();
        onConnectionChange(deviceConnected);
    }
};

class CharacteristicWriteCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pCharacteristic) override {
        const std::string uuidStd = pCharacteristic->getUUID().toString();
        const String uuid(uuidStd.c_str());
        CharacteristicConfig* characteristicInfo = server.findCharacteristicByUuid(uuid);

        if (characteristicInfo == nullptr) {
            Serial.print("Error: No characteristic found with UUID: ");
            Serial.println(uuid);
            return;
        }

        Serial.print(characteristicInfo->name);
        Serial.print('=');

        if (pCharacteristic->getLength() > 180) {
            Serial.print("<large ");
            Serial.print(pCharacteristic->getLength());
            Serial.println("B>");
        } else {
            const std::string value = pCharacteristic->getValue();
            Serial.println(value.c_str());
        }

        onCharacteristicWrite(uuid, pCharacteristic->getData(), pCharacteristic->getLength());
    }
};

void initBle() {
    ServiceConfig catDriveService("CATDRIVE", SERVICE_UUID);

    catDriveService.characteristics.emplace_back("SETTINGS", CHA_SETTINGS);
    catDriveService.characteristics.emplace_back("NAV", CHA_NAV);
    catDriveService.characteristics.emplace_back("NAV_ICON", CHA_NAV_TBT_ICON);
    catDriveService.characteristics.emplace_back("GPS_SPEED", CHA_GPS_SPEED);

    BLEDevice::init("CatDrive");
    Serial.println(BLEDevice::getMTU());
    BLEDevice::setMTU(240);

    server.services.push_back(catDriveService);

    server.bleServer = BLEDevice::createServer();
    server.bleServer->setCallbacks(new ServerCallbacks());
    CharacteristicWriteCallbacks* writeCallbacks = new CharacteristicWriteCallbacks();

    for (auto& serviceConfig : server.services) {
        const uint32_t handleCount = 4U * serviceConfig.characteristics.size();
        serviceConfig.bleService = server.bleServer->createService(
            BLEUUID(serviceConfig.uuid.c_str()),
            handleCount
        );

        for (auto& characteristicConfig : serviceConfig.characteristics) {
            const uint32_t property = BLECharacteristic::PROPERTY_WRITE;

            characteristicConfig.bleCharacteristic =
                serviceConfig.bleService->createCharacteristic(
                    characteristicConfig.uuid.c_str(),
                    property
                );

            characteristicConfig.bleCharacteristic->setCallbacks(writeCallbacks);

            BLEDescriptor* description = new BLEDescriptor((uint16_t)0x2901);
            description->setValue(std::string(characteristicConfig.name.c_str()));
            characteristicConfig.bleCharacteristic->addDescriptor(description);
        }

        serviceConfig.bleService->start();
    }

    server.bleServer->getAdvertising()->start();
}

void notifyCharacteristic(const String& uuid, uint8_t* data, size_t length) {
    CharacteristicConfig* info = server.findCharacteristicByUuid(uuid);
    if (info == nullptr || info->bleCharacteristic == nullptr) {
        Serial.print("Error: No characteristic found with UUID: ");
        Serial.println(uuid);
        return;
    }

    info->bleCharacteristic->setValue(data, length);
    info->bleCharacteristic->notify();
}

#endif // BLE_H
