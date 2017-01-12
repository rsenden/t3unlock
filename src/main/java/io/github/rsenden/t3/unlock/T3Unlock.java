package io.github.rsenden.t3.unlock;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;

import org.usb4java.BufferUtils;
import org.usb4java.Context;
import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.DeviceHandle;
import org.usb4java.DeviceList;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;

public class T3Unlock {
	private static final int vendorId = 0x04e8;
	private static final int productId = 0x61f4;
	private static final byte endpointWriteToDevice = (byte)2;
	private static final byte endpointReadFromDevice = (byte)129;
	
	public static void main(String[] args) throws Exception {
		if ( args.length < 1 ) {
			System.err.println("Usage: java -jar T3Unlock.jar <password>");
		}
		String password = args[0];
		byte[] passwordPayload = getPasswordPayload(password);
		byte[] passwordPayloadHeader1 = getPayloadHeader((byte)-42, passwordPayload.length);
		byte[] passwordPayloadHeader2 = getPayloadHeader((byte)-58, passwordPayload.length);
		byte[] relinkCommand = getRelinkCommand();

		UsbHelper helper = new UsbHelper(vendorId, productId, 0);
		try {
			helper.open(true);
			
			helper.bulkTransferWriteToDevice(endpointWriteToDevice, passwordPayloadHeader1);
			helper.bulkTransferWriteToDevice(endpointWriteToDevice, passwordPayload);
			helper.bulkTransferReadFromDevice(endpointReadFromDevice, 512);
			helper.bulkTransferWriteToDevice(endpointWriteToDevice, passwordPayloadHeader2);
			helper.bulkTransferWriteToDevice(endpointWriteToDevice, passwordPayload);
			helper.bulkTransferReadFromDevice(endpointReadFromDevice, 512);
			helper.bulkTransferWriteToDevice(endpointWriteToDevice, relinkCommand);
			helper.bulkTransferReadFromDevice(endpointReadFromDevice, 512);
		} catch ( Exception e) {
			e.printStackTrace();
		} finally {
			helper.close();
		}
		
	}

	private static final byte[] getPayloadHeader(byte param, int payloadSize) {
		return new byte[]{85, 83, 66, 67, 10, 0, 0, 0, 0, 2, 0, 0, 0, 0, 16, -123, 10, 38, 0, -42, 0, (byte)(payloadSize/512), 0, param, 0, 79, 0, -62, 0, -80, 0};
	}

	private static final byte[] getPasswordPayload(String password) {
		return getByteArray(password.getBytes(), 512);
	}
	
	private static final byte[] getByteArray(byte[] input, int length) {
		byte[] bytes = new byte[length];
		System.arraycopy(input, 0, bytes, 0, input.length);
		return bytes;
	}
	
	protected static final byte[] getRelinkCommand() {
		return new byte[]{ 85, 83, 66, 67, 11, 0, 0, 0, 0, 0, 0, 0, 0, 0, 6, -24, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
	}
	
	private static final class UsbHelper {
		private final Context context = new Context();
		private final DeviceHandle handle = new DeviceHandle();
		private final int vendorId;
		private final int productId;
		private final int interfaceNumber;
		
		public UsbHelper(int vendorId, int productId, int interfaceNumber) {
			this.vendorId = vendorId;
			this.productId = productId;
			this.interfaceNumber = interfaceNumber;
		}
		
		public final void open(boolean resetDevice) {
			initUsb();
			openDevice();
			if ( resetDevice ) {
				LibUsb.resetDevice(handle);
			}
			claimInterface();
			
		}
		
		public final void bulkTransferWriteToDevice(byte endpoint, byte[] data) {
			System.out.println("Host->Device (length="+data.length+", endpoint="+endpoint+"): "+Arrays.toString(data));
			
			IntBuffer transferred = IntBuffer.allocate(1);
			int result = LibUsb.bulkTransfer(handle, endpoint, getByteBuffer(data), transferred, 5000);
			if (result != LibUsb.SUCCESS)
			{
    				throw new LibUsbException("Unable to write data", result);
			}
			System.out.println(transferred.get() + " bytes sent");
		}
		
		private static ByteBuffer getByteBuffer(byte[] bytes) {
			ByteBuffer result = ByteBuffer.allocateDirect(bytes.length);
			result.put(bytes);
			return result;
		}

		public final byte[] bulkTransferReadFromDevice(byte endpoint,
				int size) {
			ByteBuffer buffer = BufferUtils.allocateByteBuffer(size).order(
					ByteOrder.LITTLE_ENDIAN);
			IntBuffer transferred = BufferUtils.allocateIntBuffer();
			int result = LibUsb.bulkTransfer(handle, endpoint, buffer,
					transferred, 5000);
			if (result != LibUsb.SUCCESS) {
				throw new LibUsbException("Unable to read data", result);
			}
			byte[] bytes = new byte[buffer.remaining()];
			buffer.get(bytes);
			System.out.println("Device->Host: "+Arrays.toString(bytes));
			System.out.println(transferred.get() + " bytes read from device");
			return bytes;
		}
		
		public void close() {
			int result = LibUsb.releaseInterface(handle, interfaceNumber);
			if (result != LibUsb.SUCCESS) {
				System.err.println(new LibUsbException("Unable to release interface", result).getMessage());
			}
			result = LibUsb.attachKernelDriver(handle,  interfaceNumber);
			if (result != LibUsb.SUCCESS) {
				System.err.println(new LibUsbException("Unable to re-attach kernel driver", result).getMessage());
			}
			LibUsb.close(handle);
			LibUsb.exit(context);
		}

		private void initUsb() {
			System.out.println("LibUsb.init");
			int result = LibUsb.init(context);
			if (result != LibUsb.SUCCESS)
				throw new LibUsbException("Unable to initialize libusb.", result);
		}
		
		private void openDevice() {
			System.out.println("Find Device: "+vendorId+":"+productId);
			Device device = findDevice(vendorId, productId);
			if ( device == null ) {
				throw new RuntimeException("Device not found");
			}
			System.out.println("Open Device");
			int result = LibUsb.open(device, handle);
			if (result != LibUsb.SUCCESS) {
				throw new LibUsbException("Unable to open USB device", result);
			}
		}
		
		private void claimInterface() {
			System.out.println("Detach kernel driver");
			int result = LibUsb.detachKernelDriver(handle, interfaceNumber);
			if (result != LibUsb.SUCCESS) {
				System.err.println(new LibUsbException("Unable to detach kernel driver",result).getMessage());
			}

			System.out.println("Claiming interface");
			result = LibUsb.claimInterface(handle, interfaceNumber);
			if (result != LibUsb.SUCCESS) {
				throw new LibUsbException("Unable to claim interface", result);
			}
		}

		private final Device findDevice(int vendorId, int productId) {
			// Read the USB device list
			DeviceList list = new DeviceList();
			int result = LibUsb.getDeviceList(null, list);
			if (result < 0)
				throw new LibUsbException("Unable to get device list", result);

			try {
				// Iterate over all devices and scan for the right one
				for (Device device : list) {
					DeviceDescriptor descriptor = new DeviceDescriptor();
					result = LibUsb.getDeviceDescriptor(device, descriptor);
					if (result != LibUsb.SUCCESS)
						throw new LibUsbException("Unable to read device descriptor", result);
					if (descriptor.idVendor() == vendorId && descriptor.idProduct() == productId)
						return device;
				}
			} finally {
				// Ensure the allocated device list is freed
				LibUsb.freeDeviceList(list, true);
			}

			// Device not found
			return null;
		}

	}

}
