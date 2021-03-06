/* 
 
 CsoundObj.java:
 
 Copyright (C) 2011 Victor Lazzarini, Steven Yi
 
 This file is part of Csound Android Examples.
 
 The Csound Android Examples is free software; you can redistribute it
 and/or modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 2.1 of the License, or (at your option) any later version.   
 
 Csound is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Lesser General Public License for more details.
 
 You should have received a copy of the GNU Lesser General Public
 License along with Csound; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 02111-1307 USA
 
 */

package com.csounds;

import java.io.File;
import java.util.ArrayList;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;
import android.widget.Button;
import android.widget.SeekBar;

import com.csounds.valueCacheable.CachedAccelerometer;
import com.csounds.valueCacheable.CachedButton;
import com.csounds.valueCacheable.CachedSlider;
import com.csounds.valueCacheable.CsoundValueCacheable;

import csnd.AndroidCsound;
import csnd.Csound;
import csnd.CsoundCallbackWrapper;
import csnd.CsoundMYFLTArray;
import csnd.csndConstants;

public class CsoundObj {

	private Csound csound;
	private ArrayList<CsoundValueCacheable> valuesCache;
	private ArrayList<CsoundObjCompletionListener> completionListeners;
	private boolean muted = false;
	private boolean stopped = true;
	private Thread thread;
	private boolean audioInEnabled = false;
	private boolean messageLoggingEnabled = false;
	private boolean useAudioTrack = false;
	private boolean lock = false;
	int retVal = 0;

	private CsoundCallbackWrapper callbacks;

	public CsoundObj() {
		valuesCache = new ArrayList<CsoundValueCacheable>();
		completionListeners = new ArrayList<CsoundObjCompletionListener>();
	}
	
	public CsoundObj(boolean useAudioTrack) {
		valuesCache = new ArrayList<CsoundValueCacheable>();
		completionListeners = new ArrayList<CsoundObjCompletionListener>();
		this.useAudioTrack = useAudioTrack;
	}

	/* VALUE CACHEABLE */

	// -(id<ValueCacheable>)addSwitch:(UISwitch*)uiSwitch
	// forChannelName:(NSString*)channelName;

	public boolean isAudioInEnabled() {
		return audioInEnabled;
	}

	public void setAudioInEnabled(boolean audioInEnabled) {
		this.audioInEnabled = audioInEnabled;
	}

	public boolean isMessageLoggingEnabled() {
		return messageLoggingEnabled;
	}

	public void setMessageLoggingEnabled(boolean messageLoggingEnabled) {
		this.messageLoggingEnabled = messageLoggingEnabled;
	}

	public CsoundValueCacheable addSlider(SeekBar seekBar, String channelName,
			double min, double max) {
		CachedSlider cachedSlider = new CachedSlider(seekBar, channelName, min,
				max);
		addValueCacheable(cachedSlider);

		return cachedSlider;
	}

	public CsoundValueCacheable addButton(Button button, String channelName,int type) {
		CachedButton cachedButton = new CachedButton(button, channelName, type);
		addValueCacheable(cachedButton);

		return cachedButton;
	}
	
	public CsoundValueCacheable addButton(Button button, String channelName) {
		CachedButton cachedButton = new CachedButton(button, channelName);
		addValueCacheable(cachedButton);

		return cachedButton;
	}


	// -(id<ValueCacheable>)addButton:(UIButton*)uiButton
	// forChannelName:(NSString*)channelName;

	public Csound getCsound() {
		return csound;
	}

	public boolean isMuted() {
		return muted;
	}

	public void setMuted(boolean muted) {
		this.muted = muted;
	}

	public void addValueCacheable(CsoundValueCacheable valueCacheable) {
		if(!stopped) valueCacheable.setup(this);
		while(lock);
		valuesCache.add(valueCacheable);
	}

	public void removeValueCacheable(CsoundValueCacheable valueCacheable) {
		while(lock);
		valuesCache.remove(valueCacheable);
	}

	public CsoundValueCacheable enableAccelerometer(Context context) {
		CachedAccelerometer accelerometer = new CachedAccelerometer(context);
		addValueCacheable(accelerometer);
		return accelerometer;
	}

	// -(id<ValueCacheable>)enableGyroscope;
	// -(id<ValueCacheable>)enableAttitude;

	public CsoundMYFLTArray getInputChannelPtr(String channelName) {
		CsoundMYFLTArray ptr = new CsoundMYFLTArray(1);
		getCsound().GetChannelPtr(
				ptr.GetPtr(),
				channelName,
				csndConstants.CSOUND_CONTROL_CHANNEL
						| csndConstants.CSOUND_INPUT_CHANNEL);
		return ptr;
	}

	public void sendScore(String score) {
		csound.InputMessage(score);
	}

	public void addCompletionListener(CsoundObjCompletionListener listener) {
		completionListeners.add(listener);
	}

	public void startCsound(final File csdFile) {
		stopped = false;
		thread = new Thread() {
			public void run() {
				setPriority(Thread.MAX_PRIORITY);
				// android.os.Process
				// .setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
				if(useAudioTrack == false)
				  runCsoundOpenSL(csdFile);
				else
				  runCsoundAudioTrack(csdFile);
			}
		};
		thread.start();
	}

	public void stopCsound() {
		stopped = true;
		thread = null;
	}

	public int getNumChannels() {
		return csound.GetNchnls();
	}

	public int getKsmps() {
		return csound.GetKsmps();
	}

	public int getError(){
     return retVal;
	}
	
	// -(float*)getInputChannelPtr:(NSString*)channelName;
	// -(float*)getOutputChannelPtr:(NSString*)channelName;
	// -(NSData*)getOutSamples;

	/* Render Methods */

	private void runCsoundOpenSL(File f) {

		AndroidCsound c = new AndroidCsound();
		csound = c;
		retVal = c.PreCompile();
		
		
		Log.d("CsoundAndroid", "Return Value: " + retVal);

		retVal = c.Compile(f.getAbsolutePath());
		Log.d("CsoundAndroid", "Return Value2: " + retVal);

		if (retVal == 0) {
			for (CsoundValueCacheable cacheable : valuesCache) {
				cacheable.setup(this);
			}
			stopped = false;
			for (CsoundValueCacheable cacheable : valuesCache) {
				cacheable.updateValuesToCsound();
			}

			while (c.PerformKsmps() == 0 && !stopped) {
				lock = true;
				for (CsoundValueCacheable cacheable : valuesCache) {
					cacheable.updateValuesFromCsound();
				}
        
				for (CsoundValueCacheable cacheable : valuesCache) {
					cacheable.updateValuesToCsound();
				}
				lock = false;
			}

			c.Stop();
			c.Cleanup();
			c.Reset();

			for (CsoundValueCacheable cacheable : valuesCache) {
				cacheable.cleanup();
			}

			for (CsoundObjCompletionListener listener : completionListeners) {
				listener.csoundObjComplete(this);
			}

		} else {			
			for (CsoundObjCompletionListener listener : completionListeners) {
				listener.csoundObjComplete(this);
			}
			
		}

	}

	private void runCsoundAudioTrack(File f) {

		csound = new Csound();

		retVal = csound.PreCompile();
		csound.SetHostImplementedAudioIO(1, 0);

		if (messageLoggingEnabled) {
			callbacks = new CsoundCallbackWrapper(csound) {

				@Override
				public void MessageCallback(int attr, String msg) {
					Log.d("CsoundObj", msg);
					super.MessageCallback(attr, msg);
				}

			};
		
			callbacks.SetMessageCallback();

		}

		Log.d("CsoundAndroid", "Return Value: " + retVal);

		retVal = csound.Compile(f.getAbsolutePath());
		Log.d("CsoundAndroid", "Return Value2: " + retVal);

		if (retVal == 0) {
			for (CsoundValueCacheable cacheable : valuesCache) {
				cacheable.setup(this);
			}

			int channelConfig = (csound.GetNchnls() == 2) ? AudioFormat.CHANNEL_CONFIGURATION_STEREO
					: AudioFormat.CHANNEL_CONFIGURATION_MONO;

			int channelInConfig = AudioFormat.CHANNEL_IN_MONO;

			int minSize = AudioTrack.getMinBufferSize((int) csound.GetSr(),
					channelConfig, AudioFormat.ENCODING_PCM_16BIT);

			if (audioInEnabled) {

				int recordMinSize = AudioRecord.getMinBufferSize(
						(int) csound.GetSr(), channelInConfig,
						AudioFormat.ENCODING_PCM_16BIT);

				minSize = (minSize > recordMinSize) ? minSize : recordMinSize;

			}

			AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
					(int) csound.GetSr(), channelConfig,
					AudioFormat.ENCODING_PCM_16BIT, minSize,
					AudioTrack.MODE_STREAM);
			Log.d("CsoundAndroid", "Buffer Size: " + minSize);

			AudioRecord audioRecord = null;
			CsoundMYFLTArray audioIn = null;

			if (audioInEnabled) {

				// int channelInConfig = (csound.GetNchnls() == 2) ?
				// AudioFormat.CHANNEL_IN_STEREO
				// : AudioFormat.CHANNEL_IN_MONO;

				// int channelInConfig = AudioFormat.CHANNEL_IN_MONO;
				//
				// int recordMinSize =
				// AudioRecord.getMinBufferSize((int)csound.GetSr(),
				// channelInConfig,
				// AudioFormat.ENCODING_PCM_16BIT);

				audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
						(int) csound.GetSr(), channelInConfig,
						AudioFormat.ENCODING_PCM_16BIT, minSize);

				if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
					Log.d("CsoundObj",
							"AudioRecord unable to be initialized. Error "
									+ audioRecord.getState());
					audioRecord.release();
					audioRecord = null;
				} else {
					try {
						audioRecord.startRecording();
						if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
							Log.d("CsoundObj",
									"AudioRecord unable to be initialized. Error "
											+ audioRecord.getRecordingState());

						}
						audioIn = new CsoundMYFLTArray();
						audioIn.SetPtr(csound.GetSpin());
					} catch (IllegalStateException e) {
						audioRecord.release();
						audioRecord = null;
						audioIn = null;
					}
				}
			}

			audioTrack.play();

			int counter = 0;
			int nchnls = csound.GetNchnls();
			int recBufferSize = csound.GetKsmps();
			int bufferSize = recBufferSize * nchnls;

			short[] samples = new short[bufferSize];

			float multiplier = Short.MAX_VALUE / csound.Get0dBFS();
			float recMultiplier = 1 / multiplier;

			Log.d("CsoundObj", "Multiplier: " + multiplier + " : "
					+ recMultiplier);

			stopped = false;
			for (CsoundValueCacheable cacheable : valuesCache) {
				cacheable.updateValuesToCsound();
			}

			short recordSample[] = new short[recBufferSize];

			if (audioRecord != null) {
				audioRecord.read(recordSample, 0, recBufferSize);
				for (int i = 0; i < csound.GetKsmps(); i++) {
					short sample = recordSample[i];

					if (nchnls == 2) {
						int index = i * 2;
						audioIn.SetValues(index,
								(double) (sample * recMultiplier),
								(double) (sample * recMultiplier));
					} else {
						audioIn.SetValue(i, sample);
					}
				}
			}

			while (csound.PerformKsmps() == 0 && !stopped) {

				for (int i = 0; i < csound.GetKsmps(); i++) {
					samples[counter++] = (short) (csound.GetSpoutSample(i, 0) * multiplier);

					if (nchnls > 1) {
						samples[counter++] = (short) (csound.GetSpoutSample(i,
								1) * multiplier);
					}
				}

				if (counter >= bufferSize) {
					audioTrack.write(samples, 0, bufferSize);
					counter = 0;
					// if(audioTrack.getPlayState() !=
					// AudioTrack.PLAYSTATE_PLAYING) {
					// audioTrack.play();
					//
					// }
				}

				for (CsoundValueCacheable cacheable : valuesCache) {
					cacheable.updateValuesFromCsound();
				}

				for (CsoundValueCacheable cacheable : valuesCache) {
					cacheable.updateValuesToCsound();
				}

				if (audioRecord != null) {
					audioRecord.read(recordSample, 0, recBufferSize);
					for (int i = 0; i < csound.GetKsmps(); i++) {
						short sample = recordSample[i];

						if (nchnls == 2) {
							int index = i * 2;
							audioIn.SetValues(index,
									(double) (sample * recMultiplier),
									(double) (sample * recMultiplier));
						} else {
							audioIn.SetValue(i, sample);
						}
					}
				}
			}

			audioTrack.stop();
			audioTrack.release();

			if (audioRecord != null) {
				audioRecord.stop();
				audioRecord.release();
				audioIn.Clear();
			}

			csound.Stop();
			csound.Cleanup();
			csound.Reset();

			for (CsoundValueCacheable cacheable : valuesCache) {
				cacheable.cleanup();
			}

			for (CsoundObjCompletionListener listener : completionListeners) {
				listener.csoundObjComplete(this);
			}

		}
		else {
			for (CsoundObjCompletionListener listener : completionListeners) {
				listener.csoundObjComplete(this);
			}
		}
	} 		
}
