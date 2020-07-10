part of tcn_serial;

class Tcnserial {
  static const MethodChannel _channel = MethodChannel('$NAMESPACE/methods');
  // final MethodChannel _channel = MethodChannel('$NAMESPACE/methods');
  static const EventChannel _stateChannel =
      const EventChannel('$NAMESPACE/state');

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static Future createSerialPort(Device device, int baudrate) async {
    return SerialPort(_channel, _stateChannel, device, baudrate);
  }
}
