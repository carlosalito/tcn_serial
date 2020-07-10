part of tcn_serial;

class Tcnserial {
  static const MethodChannel _channel = const MethodChannel('tcnserial');

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static Future createSerialPort(Device device, int baudrate) async {
    return SerialPort(_channel.name, device, baudrate);
  }
}
