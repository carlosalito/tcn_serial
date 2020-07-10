import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tcnserial/tcnserial.dart';

void main() {
  const MethodChannel channel = MethodChannel('tcnserial');

  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });

  test('getPlatformVersion', () async {
    expect(await Tcnserial.platformVersion, '42');
  });
}
