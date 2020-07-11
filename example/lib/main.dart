import 'dart:async';

import 'package:flutter/material.dart';
import 'package:tcnserial/tcnserial.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  SerialPort _serialPort;
  bool isPortOpened = false;
  StreamSubscription _subscription;
  bool _isElevatorFault = false;
  ElevatorStatus _elevatorStatus = ElevatorStatus.idle;

  final baudrate = 9600;
  final Device device = Device('TCN', '/dev/ttyS1');

  @override
  void dispose() {
    super.dispose();
    _subscription.cancel();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
          appBar: AppBar(
            title: const Text('Plugin example app'),
          ),
          body: Column(
            children: [
              Row(
                children: [
                  Material(
                    child: InkWell(
                      onTap: () async {
                        if (!isPortOpened) {
                          final serialPort = await Tcnserial.createSerialPort(
                              device, baudrate);
                          bool openResult = await serialPort.open();
                          setState(() {
                            _serialPort = serialPort;
                            isPortOpened = openResult;
                          });

                          _serialPort.dataSerial.listen((recv) {
                            if (recv != null) {
                              List<String> recvData = _formatReceivedData(recv);
                              print('RECEBIDO e Transformado $recvData');
                              _processReceivedData(recvData);
                            }
                          });
                          
                        }
                      },
                      child: Text('CONNECTAR', style: TextStyle(fontSize: 25)),
                    ),
                  ),
                  SizedBox(
                    width: 50,
                  ),
                  Material(
                    child: InkWell(
                      onTap: () async {
                        if (isPortOpened) {
                          bool closeResult = await _serialPort.close();
                          setState(() {
                            _serialPort = null;
                            isPortOpened = !closeResult;
                          });
                        }
                      },
                      child: Text(
                        'DESCONECTAR',
                        style: TextStyle(fontSize: 25),
                      ),
                    ),
                  ),
                ],
              ),
              Material(
                child: InkWell(
                  onTap: () async {
                    _requestShipment('1');
                  },
                  child: Text(
                    'SLOT 01',
                    style: TextStyle(fontSize: 25),
                  ),
                ),
              ),
              SizedBox(height: 50),
              Material(
                child: InkWell(
                  onTap: () async {
                    _requestShipment('4');
                  },
                  child: Text(
                    'SLOT 04',
                    style: TextStyle(fontSize: 25),
                  ),
                ),
              ),
              SizedBox(height: 50),
              Material(
                child: InkWell(
                  onTap: () async {
                    _requestShipment('5');
                  },
                  child: Text(
                    'SLOT 05',
                    style: TextStyle(fontSize: 25),
                  ),
                ),
              ),
              SizedBox(height: 50),
              Material(
                child: InkWell(
                  onTap: () async {
                    _clearFault();
                  },
                  child: Text(
                    'LIMPAR FALTA',
                    style: TextStyle(fontSize: 25),
                  ),
                ),
              ),
            ],
          )),
    );
  }

  void _clearFault() async {
    _serialPort.clearElevatorFault();
    await Future.delayed(Duration(milliseconds: 1000), () {
      // _serialPort.getElevatorStatus();
    });
  }

  void _requestShipment(String slot) async {
    _serialPort.getElevatorStatus();
    await Future.delayed(Duration(milliseconds: 1000), () {
      if (_isElevatorFault || _elevatorStatus != ElevatorStatus.idle) {        
       print(_isElevatorFault ? 'Não podemos continuar com a retirada do produto, elevador em falta' :
       'Elevador trabalhando...');
      } else {
        _serialPort.shipment(slot);
      }
    });
  }

  List<String> _formatReceivedData(recv) {
    RegExp rx = new RegExp(r".{1,2}(?=(.{2})+(?!.))|.{1,2}$");
    return rx.allMatches(recv).map((m) => m.group(0)).toList();
  }

  String intToHex(int i) {
    return i.toRadixString(16).toUpperCase();
  }

  void _processReceivedData(List<String> data) {
    //elevator process
    final String dataString = data.join('');

    if (dataString.startsWith('02050100')) {
      final ElevatorStatus elevatorStatus =
          ElevatorStatus.values[int.parse(data[4])];

      setState(() {
        _elevatorStatus = elevatorStatus;
      });
      print('STATUS DO ELEVADOR - $elevatorStatus');
      _checkErrorCode(data);
      //shipment process
    } else if (dataString.startsWith('02050200')) {
      final String errCode = data[5];
      if (errCode == '0') {
        print('SUCCESS REQUEST SHIPMENT');
      } else {
        print('ERR REQUEST SHIPMENT - $errCode');
      }
    } else if (dataString.startsWith('020550')) {
      print('CLEAR ELEVATOR FAULT');
      setState(() {
        _isElevatorFault = false;
      });
    }
  }

  void _checkErrorCode(List<String> data) {
    String err = data[5];    
    if (err == '00') {
      print('NO ELEVATOR FAULT');
    } else {
      print('ELEVATOR FAULT - $err');
      setState(() {
        _isElevatorFault = true;
      });
    }
  }
}

enum ElevatorStatus { idle, busy, waitPickup }
