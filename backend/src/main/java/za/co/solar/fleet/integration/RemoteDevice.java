package za.co.solar.fleet.integration;

public record RemoteDevice(String externalId, String serialNumber, String model, String firmwareVersion, double ratedPowerKw) {}
