part of tcn_serial;

class SerialPort {
  MethodChannel _channel;
  EventChannel _eventChannel;
  Stream _eventStream;
  Device device;
  int baudrate;
  bool _deviceConnected;

  SerialPort(String methodChannelName, Device device, int baudrate) {
    this.device = device;
    this.baudrate = baudrate;
    this._channel = MethodChannel(methodChannelName);
    this._eventChannel = EventChannel("$methodChannelName/event");
    this._deviceConnected = false;
  }

  bool get isConnected => _deviceConnected;

  /// Stream(Event) coming from Android
  Stream<Uint8List> get receiveStream {
    _eventStream = _eventChannel
        .receiveBroadcastStream()
        .map<Uint8List>((dynamic value) => value);
    return _eventStream;
  }

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