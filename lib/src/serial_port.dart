part of tcn_serial;

class SerialPort {
  MethodChannel _channel;
  EventChannel _eventChannel;
  Device device;
  int baudrate;
  bool _deviceConnected;

  BehaviorSubject<String> _dataSerialBS =
      new BehaviorSubject<String>.seeded(null);
  Stream<String> get dataSerial => _dataSerialBS.stream;

  SerialPort(MethodChannel channel, EventChannel eventChannel, Device device,
      int baudrate) {
    this.device = device;
    this.baudrate = baudrate;
    this._channel = channel;
    this._eventChannel = eventChannel;
    this._deviceConnected = false;

    this._channel.setMethodCallHandler((MethodCall call) {
      print('call.method ${call.method}');
      print('call.arguments ${call.arguments}');
      _dataSerialBS.sink.add(call.arguments);
      return;
    });
  }

  bool get isConnected => _deviceConnected;

  @override
  String toString() {
    return "SerialPort($device, $baudrate)";
  }

  /// Open device
  Future<bool> open() async {
    bool openResult = await _channel.invokeMethod(
        "open", {'devicePath': device.path, 'baudrate': baudrate});

    if (openResult) {
      _deviceConnected = true;
    }

    return openResult;
  }

  /// Close device
  Future<bool> close() async {
    bool closeResult = await _channel.invokeMethod("close");
    if (closeResult) {
      _dataSerialBS.close();
      _deviceConnected = false;
    }

    return closeResult;
  }

  Future<void> getElevatorStatus() async {
    return await _channel.invokeMethod(
        'tcnCommand', jsonEncode({'command': 'statusElevator', "data": ''}));
  }

  Future<void> shipment(String slot) async {
    return await _channel.invokeMethod(
        'tcnCommand', jsonEncode({'command': 'shipment', "data": slot}));
  }

  Future<void> clearElevatorFault() async {
    return await _channel.invokeMethod('tcnCommand',
        jsonEncode({'command': 'clearElevatorFault', "data": ''}));
  }

  Future<void> backElevatorToOrigin() async {
    return await _channel.invokeMethod('tcnCommand',
        jsonEncode({'command': 'backElevatorToOrigin', "data": ''}));
  }
}

class Device {
  String name;
  String path;

  Device(this.name, this.path);

  @override
  String toString() {
    return "Device($name, $path)";
  }
}
