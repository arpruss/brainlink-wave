package mobi.omegacentauri.blwave;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.graphics.drawable.StateListDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class BLWave extends Activity {
	private static final String PREF_LAST_DEVICE = "lastDevice";
	private BluetoothAdapter btAdapter;
	private TextView message;
	private SharedPreferences options;
	private ArrayAdapter<String> deviceSelectionAdapter;
	private Spinner deviceSpinner;
	private Spinner btSpinner;
	private Spinner fwSpinner;
	private ArrayList<BluetoothDevice> devs;
	private static final String PREF_DISCLAIMED_VERSION = "disclaimed";
	private WaveChannel[] channels;
	private BTDataLink link = null;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		options = PreferenceManager.getDefaultSharedPreferences(this);
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		Log.v("BLFW", "OnCreate");
		
		setContentView(R.layout.main);
		
		deviceSpinner = (Spinner)findViewById(R.id.device_spinner);
		
		channels = new WaveChannel[2];
		channels[0] = new WaveChannel(0, R.id.freq_1, R.id.type_1, R.id.duty_1_layout, R.id.duty_1, R.id.ampl_layout_1, R.id.ampl_1, R.id.data_layout_1, R.id.data_1);
		channels[1] = new WaveChannel(1, R.id.freq_2, R.id.type_2, R.id.duty_2_layout, R.id.duty_2, R.id.ampl_layout_2, R.id.ampl_2, R.id.data_layout_2, R.id.data_2);
		
		disclaimer();
	}
	
	@Override
	public void onPause() {
		super.onPause();
		for (WaveChannel c : channels)
			c.onPause();
		
		if (link != null) {
			link.stop();
			link = null;
		}
	}
	
	public void play1(View v) {
		channels[0].play();
	}
	
	public void play2(View v) {
		channels[1].play();
	}
	
	public void stop1(View v) {
		channels[0].stop();
	}
	
	public void stop2(View v) {
		channels[1].stop();
	}
	
	public void clickedLicense(View v) {
		AlertDialog.Builder b = new AlertDialog.Builder(this);
		b.setTitle("License for Brainlink Firmware");
		b.setMessage("This work is copyright BirdBrain Technologies (with modifications by Alexander Pruss) and licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. "+
"To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/ or send a letter to "+
"Creative Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.");
		b.create().show();
	}
	
	public void disclaimer() {
		int v;
		try {
			v = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
			if (v == options.getInt(PREF_DISCLAIMED_VERSION , 0));
				return;
		} catch (NameNotFoundException e) {
			v = 0;
		}

		AlertDialog.Builder b = new AlertDialog.Builder(this);
		b.setTitle("Disclaimer");
		b.setMessage("USE THIS ONLY AT YOUR OWN RISK.  Omega Centauri Software takes no responsibility for any "+
		"damage to data, hardware or persons.  Do you agree?");
		b.setNegativeButton("Disagree", new OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				BLWave.this.finish();
			}
		});
		final int v0 = v;
		b.setPositiveButton("Agree",  new OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				options.edit().putInt(PREF_DISCLAIMED_VERSION, v0);
			}
		});
		b.create().show();
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		devs = new ArrayList<BluetoothDevice>();
		devs.addAll(btAdapter.getBondedDevices());
		Collections.sort(devs, new Comparator<BluetoothDevice>(){
			@Override
			public int compare(BluetoothDevice lhs, BluetoothDevice rhs) {
				return String.CASE_INSENSITIVE_ORDER.compare(lhs.getName(), rhs.getName());
			}});
		ArrayList<String> devLabels = new ArrayList<String>();
		for (BluetoothDevice d : devs) 
			devLabels.add(d.getName()+" ("+d.getAddress()+")");
		
		deviceSelectionAdapter = new ArrayAdapter<String>(this, 
				android.R.layout.simple_spinner_item, devLabels);
		deviceSelectionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		deviceSpinner.setAdapter(deviceSelectionAdapter);
		String lastDev = options.getString(PREF_LAST_DEVICE, "(none)");
		
		for (int i = 0 ; i < devs.size() ; i++) {
			if (devs.get(i).getName().equals("RN42-A308")) {
				deviceSpinner.setSelection(i);
				break;
			}
		}
		
		for (int i = 0 ; i < devs.size() ; i++) {
			if (devs.get(i).getAddress().equals(lastDev))
				deviceSpinner.setSelection(i);
		} 
		
		deviceSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1,
					int position, long arg3) {
				options.edit().putString(PREF_LAST_DEVICE, devs.get(position).getAddress()).commit();				
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				// TODO Auto-generated method stub
				
			}
		});
		
		if (devs.size() == 0)
			message.setText("Bluetooth turned off or no devices paired.");

		for (WaveChannel c : channels)
			c.onResume();

		
	}
	
	class BluetoothTask extends AsyncTask<byte[], String, String>{
		private ProgressDialog progressDialog;
		private BluetoothDevice device;
		private static final int FLASH_SIZE_BYTES = 16384;
		private static final int FLASH_PAGE_SIZE_WORDS = 128;
		private String lastMessage = "";

		public BluetoothTask(Context c, BluetoothDevice device) {
			this.device = device;
			progressDialog = new ProgressDialog(c);
			progressDialog.setCancelable(false);
			progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		}
		
		@Override
		public void onProgressUpdate(String... msg) {
			progressDialog.setMessage(msg[0]);
			if (msg.length <= 3) {
				progressDialog.setProgress(ProgressDialog.STYLE_SPINNER);
			}
			else {
				progressDialog.setProgress(ProgressDialog.STYLE_HORIZONTAL);
				try {
					progressDialog.setMax(Integer.parseInt(msg[2]));
					progressDialog.setProgress(Integer.parseInt(msg[3]));
				}
				catch (Exception e) {					
				}
			}
			progressDialog.show();
		}
		
		public void progressValue(int current, int max) {
			publishProgress(lastMessage, ""+current, ""+max);
		}
		
		@Override
		protected String doInBackground(byte[]... args) {
			try {
				if (link != null && !link.address.equals(device.getAddress())) {
					link.stop();
					link = null;
				}
				
				if (link == null) {
					publishProgress("Connecting");

					link = new BTDataLink(device);
				}
				
				publishProgress("Sending command");
				if (! link.transmit((byte)'*')) {
					publishProgress("Reconnecting");

					link = new BTDataLink(device);

					publishProgress("Sending command");
				}

				link.clearBuffer();
				link.transmit(args[0]);
				String cmd = new String(args[0]);
				byte[] response = new byte[args[0].length + 4];
				link.readBytes(response, 1000);
				String rsp = new String(response);
				if (!rsp.contains(cmd)) {
					return "Error sending data to Brainlink";
				}
				else if (rsp.contains("ERR")) {
					return "Command not supported by Brainlink";
				}
				else {
					return "Command sent";
				}
			}
			catch (Exception e) {
				return e.toString();
			}
		}

		@Override
		protected void onPostExecute(String message) {
			Toast.makeText(BLWave.this, message, Toast.LENGTH_LONG).show();
			progressDialog.dismiss();
		}
		
	}

	public static void sleep(int ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e2) {
		}
	}
	

	private class WaveChannel {
		int index;
		EditText freqView;
		Spinner type;
		View dutyLayout;
		EditText duty;
		View amplLayout;
		EditText ampl;
		EditText data;
		View dataLayout;
		char[] types = { 's', 'q', 't', 'a' };
		static final String FREQ = "freq";
		static final String TYPE = "type";
		static final String DUTY = "duty";
		static final String AMPL = "ampl";
		static final String DATA = "data";
		
		public WaveChannel(int index, int freqID, int typeID, int dutyLayoutID, int dutyID, int amplLayoutID, int amplID, int dataLayoutID, int dataID) {
			this.index = index;
			freqView = (EditText)BLWave.this.findViewById(freqID);
			type = (Spinner)BLWave.this.findViewById(typeID);
			dutyLayout = findViewById(dutyLayoutID);
			duty = (EditText)BLWave.this.findViewById(dutyID);
			amplLayout = findViewById(amplLayoutID);
			ampl = (EditText)BLWave.this.findViewById(amplID);
			data = (EditText)BLWave.this.findViewById(dataID);
			dataLayout = findViewById(dataLayoutID);

			type.setOnItemSelectedListener(new OnItemSelectedListener() {

				@Override
				public void onItemSelected(AdapterView<?> arg0, View arg1,
						int position, long arg3) {
					updateViews();
				}

				@Override
				public void onNothingSelected(AdapterView<?> arg0) {
					// TODO Auto-generated method stub
					
				}
			});
			
			freqView.setText(options.getString(FREQ+index, "1000"));
			int t = options.getInt(TYPE+index, 's');
			for (int i=0; i<types.length; i++)
				if (t == types[i]) {
					type.setSelection(i);
					break;
				}
			duty.setText(options.getString(DUTY+index, "32"));
			ampl.setText(options.getString(AMPL+index, "255"));
			data.setText(options.getString(DATA+index, ""));					
		}
		
		public void updateViews() {
			int v = type.getSelectedItemPosition();
			if (v < 0)
				return;
			dutyLayout.setVisibility(types[v] == 'q' ? View.VISIBLE : View.GONE);
			amplLayout.setVisibility(types[v] == 'a' ? View.GONE : View.VISIBLE);
			dataLayout.setVisibility(types[v] == 'a' ? View.VISIBLE : View.GONE);
		}
		
		public void onResume() {
			updateViews();
		}
		
		public void onPause() {
			SharedPreferences.Editor ed = options.edit();
			ed.putString(FREQ+index, freqView.getText().toString());
			if (type.getSelectedItemPosition() >= 0)
				ed.putInt(TYPE+index, types[type.getSelectedItemPosition()]);
			ed.putString(DUTY+index, duty.getText().toString());
			ed.putString(AMPL+index, ampl.getText().toString());
			ed.putString(DATA+index, data.getText().toString());
			ed.commit();
		}
		
		public void play() {
			int pos = deviceSpinner.getSelectedItemPosition();
			if (pos < 0) {
				Toast.makeText(BLWave.this, "Select a device", Toast.LENGTH_LONG).show();
			}
			else {
				byte[] cmd = generateCommand();
				if (cmd != null) 
					new BluetoothTask(BLWave.this, devs.get(pos)).execute(cmd);
			}
		}
		
		public byte[] generateArbitraryWaveCommand(int freq) {
			String[] dd = data.getText().toString().replaceAll("[\\s\\n\\r,;]+", " ").replaceAll("^ ", "").replaceAll(" $", "").split(" ");
			if (dd.length < 1 || dd.length > 64) {
				Toast.makeText(BLWave.this, "Invalid data length", Toast.LENGTH_LONG).show();
				return null;
			}
			byte[] cmd = new byte[6+dd.length];
			cmd[0] = 'W';
			cmd[1] = (byte)('0'+index);
			cmd[2] = (byte)(freq >> 16);
			cmd[3] = (byte)((freq >> 8)&0xFF);
			cmd[4] = (byte)(freq & 0xFF);
			cmd[5] = (byte)dd.length;
			
			for (int i=0; i<dd.length; i++) {
				try {
					int datum = Integer.parseInt(dd[i]);
					if (datum < 0 || datum > 255)
						throw new NumberFormatException();
					cmd[6+i] = (byte)datum;
				}
				catch(NumberFormatException e) {
					Toast.makeText(BLWave.this, "Invalid datum", Toast.LENGTH_LONG).show();
					return null;
				}
			}
			
			return cmd;
		}
		
		public byte[] generateCommand() {
			int freq;
			
			try {
				freq = Integer.parseInt(freqView.getText().toString());
				if (freq < 1 || freq > 500000) 
					throw new NumberFormatException();
			} catch(NumberFormatException e) {
				Toast.makeText(BLWave.this, "Invalid frequency", Toast.LENGTH_LONG).show();
				return null;
			}
			
			if (type.getSelectedItemPosition() < 0) {
				Toast.makeText(BLWave.this, "Select waveform type", Toast.LENGTH_LONG).show();
				return null;				
			}
			
			char t = types[type.getSelectedItemPosition()];
			if (t == 'a') {
				return generateArbitraryWaveCommand(freq);
			}
			
			byte[] cmd = new byte[8];
			cmd[0] = 'w';
			cmd[1] = (byte)('0' + index);
			cmd[2] = (byte)t;
			if (t == 'q') {
				try {
					int d = Integer.parseInt(duty.getText().toString());
					if (d < 0 || d > 63)
						throw new NumberFormatException();
					cmd[3] = (byte)d;
				} catch(NumberFormatException e) {
					Toast.makeText(BLWave.this, "Invalid duty value", Toast.LENGTH_LONG).show();
				}
			}
			else {
				cmd[3] = 0;
			}
			
			try {
				int a = Integer.parseInt(ampl.getText().toString());
				if (a < 0 || a > 255)
					throw new NumberFormatException();
				cmd[4] = (byte)a;
			} catch(NumberFormatException e) {
				Toast.makeText(BLWave.this, "Invalid amplitude value", Toast.LENGTH_LONG).show();
			}
			
			cmd[5] = (byte)(freq >> 16);
			cmd[6] = (byte)((freq >> 8)&0xFF);
			cmd[7] = (byte)(freq & 0xFF);
			
			return cmd;
		}
		
		public void stop() {
			int pos = deviceSpinner.getSelectedItemPosition();
			if (pos < 0) {
				Toast.makeText(BLWave.this, "Select a device", Toast.LENGTH_LONG).show();
			}
			else {
				byte[] cmd = new byte[] { '@', (byte)('0'+index) }; 
				if (cmd != null) 
					new BluetoothTask(BLWave.this, devs.get(pos)).execute(cmd);
			}
		}
	}
}
