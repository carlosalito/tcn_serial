package io.idhea.tcnserial;

import android.serialport.SerialPort;
import android.serialport.SerialPortFinder;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.lang.Thread;
import java.lang.Runnable;
import java.util.Arrays;
import java.util.ArrayList;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/** TcnserialPlugin */
public class TcnserialPlugin implements FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler {
  private static final String TAG = "TCNSerial";
  private MethodChannel channel;
  private EventChannel.EventSink mEventSink;
  protected OutputStream mOutputStream;
  private InputStream mInputStream;
  private ReadThread mReadThread;
  private Handler mHandler = new Handler(Looper.getMainLooper());
  protected SerialPort mSerialPort;

  TcnserialPlugin(Registrar registrar) {
    final EventChannel eventChannel = new EventChannel(registrar.messenger(), "tcnserial/event");
    eventChannel.setStreamHandler(this);
  }

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    channel = new MethodChannel(flutterPluginBinding.getFlutterEngine().getDartExecutor(), "tcnserial");
    channel.setMethodCallHandler(this);
  }

  public static void registerWith(Registrar registrar) {
    final MethodChannel channel = new MethodChannel(registrar.messenger(), "tcnserial");
    channel.setMethodCallHandler(new TcnserialPlugin(registrar));
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    switch (call.method) {
      case "getPlatformVersion":
        result.success("Android " + android.os.Build.VERSION.RELEASE);
        break;
      case "open":
        final String devicePath = call.argument("devicePath");
        final int baudrate = call.argument("baudrate");
        Log.d(TAG, "Open " + devicePath + ", baudrate: " + baudrate);
        Boolean openResult = openDevice(devicePath, baudrate);
        result.success(openResult);
        break;
      case "close":
        Boolean closeResult = closeDevice();
        result.success(closeResult);
        break;
      case "tcnCommand":
        try {
          Log.d(TAG, "call.arguments() " + call.arguments() );
          JSONObject obj = new JSONObject((String) call.arguments());
          Log.d(TAG, "obj " + obj );
          processTCNCommand(obj);
          result.success(true);
        } catch (JSONException e) {
          e.printStackTrace();
        }
        break;
      default:
        result.notImplemented();
        break;
    }
  }

  private void getElevatorStatus() {
    byte[] bytesToSend = {0x02,0x03,0x01,0x00,0x00,0x03,0x03};
    try {
      mOutputStream.write(bytesToSend, 0, 7);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void processTCNCommand(JSONObject obj) {
    try {
      byte[] bytesToSend;
      String command = (String) obj.get("command");
      switch (command) {
        case "statusElevator":
          getElevatorStatus();
          break;
        case "shipment":
          int slot = Integer.parseInt((String) obj.get("data"));
          bytesToSend = new byte[]{0x02,0x06,0x02,0x00, (byte)(slot),0x00,0x00,(byte) (byte)(slot),0x03, 0x05};
          mOutputStream.write(bytesToSend, 0, 10);
          break;
        case "clearElevatorFault":
          bytesToSend = new byte[]{0x02, 0x03, 0x50, 0x00, 0x00, 0x03, 0x05};
          mOutputStream.write(bytesToSend, 0, 7);
          break;
        case "backElevatorToOrigin":
          bytesToSend = new byte[]{0x02, 0x03, 0x05,0x00, 0x00, 0x03, 0x05};
          mOutputStream.write(bytesToSend, 0, 7);
          break;
      }
    } catch (IOException e) {
      Log.e(TAG, e.toString());
    } catch (JSONException e) {
      Log.e(TAG, e.toString());
    }
  }

  private Boolean openDevice(String devicePath, int baudrate) {
    if (mSerialPort == null) {
      /* Check parameters */
      if ((devicePath.length() == 0) || (baudrate == -1)) {
        return false;
      }

      /* Open the serial port */
      try {
        mSerialPort = new SerialPort(new File(devicePath), baudrate, 0);
        mOutputStream = mSerialPort.getOutputStream();
        mInputStream = mSerialPort.getInputStream();
        mReadThread = new ReadThread();
        mReadThread.start();
        return true;
      } catch (Exception e) {
        Log.e(TAG, e.toString());
        return false;
      }
    }
    return false;
  }

  private Boolean closeDevice() {
    if (mSerialPort != null) {
      mSerialPort.close();
      mSerialPort = null;
      return true;
    }
    return false;
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
  }

  private class ReadThread extends Thread {
    @Override
    public void run() {
      super.run();
      while (!isInterrupted()) {
        int size;
        try {
          byte[] buffer = new byte[64];
          if (mInputStream == null)
            return;
          size = mInputStream.read(buffer);
          Log.d(TAG, "read size: " + String.valueOf(size));
          if (size > 0) {
            onDataReceived(buffer, size);
          }
        } catch (IOException e) {
          e.printStackTrace();
          return;
        }
      }
    }
  }

  protected void onDataReceived(final byte[] buffer, final int size) {
    if (mEventSink != null) {
      mHandler.post(new Runnable() {
        @Override
        public void run() {
          Log.d(TAG, "eventsink: " + buffer.toString());
          mEventSink.success(Arrays.copyOfRange(buffer, 0, size));
        }
      });
    }
  }

  @Override
  public void onListen(Object o, EventChannel.EventSink eventSink) {
    mEventSink = eventSink;
  }

  @Override
  public void onCancel(Object o) {
    mEventSink = null;
  }

}