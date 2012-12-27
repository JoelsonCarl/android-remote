package com.joelsoncarl.androidremote;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import android.os.AsyncTask;
import android.widget.TextView;

/**
 * This handles the RFB Protocol.  The reference document used
 * during implementation was at http://www.realvnc.com/docs/rfbproto.pdf
 */
public class RfbClient {

	/** The TextView used for displaying messages. */
	public TextView m_connectMsg;
	/** A reference to main activity from whence we came */
	public MainActivity m_mainActivity;

	/** Socket connection to the RFB Server */
	private Socket m_rfbServerSock;
	/** Input Stream for reading data from the RFB Server */
	private DataInputStream m_rfbInput;
	/** Output Stream for sending data to the RFB Server */
	private DataOutputStream m_rfbOutput;
	/** Holds data to send or data read from the server */
	private byte [] m_data;
	/** Boolean indicator of read/send success */
	private boolean m_dataResult;
	/** Holds the length of strings returned from the server */
	private int m_reasonLength;
	
	/** Holds the RFB Protocol Version */
	private short m_protocolVersion;
	/** Holds the RFB Security Type */
	private int m_securityType;

	/**
	 * Constructor for RfbClient
	 * @param ma The MainActivity from whence we came
	 */
	RfbClient(MainActivity ma) {
		m_mainActivity = ma;
		m_connectMsg = (TextView) m_mainActivity.findViewById(R.id.connection_message);
		m_rfbServerSock = null;
		m_protocolVersion = 0;
		m_data = new byte [64];
		m_dataResult = false;
		m_securityType = 0;
	}
	
	/**
	 * Called from the AsyncTask that established the socket connection
	 * @param sock The Socket connection to the RFB Server
	 */
	public void connectDone(Socket sock) {
		m_rfbServerSock = sock;
		m_connectMsg.setText(m_mainActivity.getResources().getString(R.string.connected));
		if (m_rfbServerSock != null) {
			try {
				m_rfbInput = new DataInputStream(m_rfbServerSock.getInputStream());
				m_rfbOutput = new DataOutputStream(m_rfbServerSock.getOutputStream());
				new RfbHandshakeTask().execute(0, 12);
			}
			catch (IOException e) {
				m_connectMsg.setText("Error in input or output stream");
			}
		}
	}
	
	/**
	 * Closes the input/output streams and the socket
	 */
	public void closeConnection() {
		try {
			if (m_rfbInput != null) {
				m_rfbInput.close();
			}
			if (m_rfbOutput != null) {
				m_rfbOutput.close();
			}
			if (m_rfbServerSock != null) {
				m_rfbServerSock.close();
			}
			this.m_connectMsg.setText(m_mainActivity.getResources().getString(R.string.disconnected));
		} catch (IOException e) {
			m_connectMsg.setText("Error closing connection");
		}
	}
	
	/**
	 * Generic AsyncTask for reading data from the RFB Server
	 * 
	 * Argument to execute() should be an Integer array with
	 * the first element being the offset of where to start
	 * storing data in the byte array, and the second element
	 * being the number of bytes to read.
	 */
	private class RfbReadDataTask extends AsyncTask<Integer, Void, Boolean> {
		protected Boolean doInBackground(Integer... ints) {
			try {
				m_rfbInput.readFully(m_data, ints[0], ints[1]);
			} catch (IOException e) {
				m_connectMsg.setText("Error: RfbReadDataTask()");
				return false;
			}
			return true;
		}
		
		protected void onPostExecute(Boolean result) {
			m_dataResult = result;
		}
	}
	
	/**
	 * Generic AsyncTask for sending data to the RFB Server
	 * 
	 * Argument to execute() should be an Integer array with
	 * the first element being the offset of where to start
	 * reading data in the byte array, and the second element
	 * being the number of bytes to send.
	 */
	private class RfbSendDataTask extends AsyncTask<Integer, Void, Boolean> {
		protected Boolean doInBackground(Integer... ints) {
			try {
				m_rfbOutput.write(m_data, ints[0], ints[1]);
			} catch (IOException e) {
				m_connectMsg.setText("Error: RfbSendDataTask()");
				return false;
			}
			return true;
		}
		
		protected void onPostExecute(Boolean result) {
			m_dataResult = result;
		}
	}
	
	/**
	 * This handles reading the initial handshake message from
	 * the RFB server ("RFB 003.00x\n" where 'x' is 3, 7, or 8).
	 * Grabs the 'x' to record the protocol version.
	 */
	private class RfbHandshakeTask extends RfbReadDataTask {
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			if (m_dataResult) {
				//Uncomment line below to force test protocol 3.3
				//(assumes we were getting 3.8)
				//m_data[10] = (byte) (m_data[10] - 5);
				m_protocolVersion = (short)((0xFF & ((int) m_data[10])) - 0x30);
				m_connectMsg.setText("Protocol Version: " + Short.toString(m_protocolVersion));
				new RfbHandshakeResponseTask().execute(0, 12);
			}
			else {
				m_connectMsg.setText("Error Receiving RFB Handshake");
			}
		}
	}
	
	/**
	 * Responds by echoing back the handshake information (protocol allows
	 * selecting a lower version, but we will just use the highest provided
	 * by the server)
	 */
	private class RfbHandshakeResponseTask extends RfbSendDataTask {
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			if (m_dataResult) {
				// Version 3.7 onwards - The server sends a single
				// byte with the number of security types supported,
				// so we need to grab that single byte first
				if (m_protocolVersion >= 7) {
					new RfbSecurityTask().execute(0, 1);
				}
				// Version 3.3 - The server decides the security type
				// and sends a single 4 byte word.
				else {
					new RfbSecurityTask().execute(0, 4);
				}
			}
			else {
				m_connectMsg.setText("Error Sending RFB Handshake Response");
			}
		}
	}
	
	/**
	 * Reads the number of security types (3.7+) or the server-dictated
	 * security type (3.3)
	 */
	private class RfbSecurityTask extends RfbReadDataTask {
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			if (m_dataResult) {
				// Version 3.7+
				if (m_protocolVersion >= 7) {
					short numberSecurityTypes = (short) (0xFF & ((int) m_data[0]));
					m_connectMsg.setText("numberSecurityTypes = " + Short.toString(numberSecurityTypes));
					// A number of security types value of 0 means failure,
					// and the server will be sending us a reason string
					if (numberSecurityTypes == 0) {
						new RfbFailureReasonLengthTask().execute(0, 4);
					}
					// We need to read the array of bytes to get the list
					// of security types supported
					else {
						// TODO: continue 3.7 Rfb Security Process
						//new RfbSecurityTypesTask().execute(0, (int) numberSecurityTypes);
					}
				}
				// Version 3.3
				else {
					// Combine the four bytes into a single int representing
					// the security type chosen by the server
					short securityTypeByte3 = (short) (0xFF & ((int) m_data[0]));
					short securityTypeByte2 = (short) (0xFF & ((int) m_data[1]));
					short securityTypeByte1 = (short) (0xFF & ((int) m_data[2]));
					short securityTypeByte0 = (short) (0xFF & ((int) m_data[3]));
					int securityType = 0;
					securityType = (securityTypeByte3 << 24)
									| (securityTypeByte2 << 16)
									| (securityTypeByte1 << 8)
									| (securityTypeByte0);
					m_connectMsg.setText("securityType = " + Integer.toString(securityType));
					// A type of 0 indicates failure, and the server will
					// be sending a reason string
					if (securityType == 0) {
						new RfbFailureReasonLengthTask().execute(0, 4);
					}
					else {
						m_securityType = securityType;
						// TODO: Continue 3.3 Rfb Security Process
						//new RfbSecurityTypesTask().execute(0, 0);
					}
				}
			}
			else {
				m_connectMsg.setText("Error Receiving Security Information");
			}
		}
	}
	
	/**
	 * There was a failure and the server is sending a reason string.
	 * We first need to read the 4 byte word that tells how long the
	 * reason string will be.
	 */
	private class RfbFailureReasonLengthTask extends RfbReadDataTask {
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			if (m_dataResult) {
					short failureReasonLengthByte3 = (short) (0xFF & ((int) m_data[0]));
					short failureReasonLengthByte2 = (short) (0xFF & ((int) m_data[1]));
					short failureReasonLengthByte1 = (short) (0xFF & ((int) m_data[2]));
					short failureReasonLengthByte0 = (short) (0xFF & ((int) m_data[3]));
					int failureReasonLength = 0;
					failureReasonLength = (failureReasonLengthByte3 << 24)
									| (failureReasonLengthByte2 << 16)
									| (failureReasonLengthByte1 << 8)
									| (failureReasonLengthByte0);
					m_reasonLength = failureReasonLength;
					new RfbFailureReasonTask().execute(0, failureReasonLength);
			}
			else {
				m_connectMsg.setText("Error Receiving Failure Reason Length");
			}
		}
	}
	
	/**
	 * Read the actual reason string
	 */
	private class RfbFailureReasonTask extends RfbReadDataTask {
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			if (m_dataResult) {
				StringBuilder s = new StringBuilder();
				for (int i = 0; i < m_reasonLength; i++) {
					s.append((char) m_data[i]);
				}
				m_connectMsg.setText(s.toString());
			}
			else {
				m_connectMsg.setText("Error Receiving Failure Reason");
			}
		}
	}

}
