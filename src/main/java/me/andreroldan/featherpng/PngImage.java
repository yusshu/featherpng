package me.andreroldan.featherpng;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a png image
 *
 * @author rayvanderborght
 */
public final class PngImage {
	// The first eight bytes of a PNG datastream always contain the following (decimal) values:
	//     137  80  78  71  13  10  26  10
	//      ?    P   N   G  \r  \n   ?  \n
	public static final long SIGNATURE = 0x89504E470D0A1A0AL;

	private final List<PngChunk> chunks = new ArrayList<>();

	private long width;
	private long height;
	private long length;

	private short bitDepth;
	private short colorType;
	private short interlace;

	private PngChunk palette;
	private PngImageType imageType;

	public static PngImage read(final InputStream ins) throws IOException {
		PngImage image = new PngImage();
		DataInputStream dis = new DataInputStream(ins);

		// read PNG signature
		{
			final long signature = dis.readLong();
			if (signature != PngImage.SIGNATURE) {
				throw new PngException("Bad png signature");
			}
		}

		int len;
		PngChunk chunk;

		do {
			len = dis.readInt();
			final int type = dis.readInt();
			final byte[] data = image.getChunkData(dis, len);
			long crc = image.getChunkCrc(dis);

			chunk = new PngChunk(type, data);

			if (!chunk.verifyCRC(crc)) {
				throw new PngException("Corrupted file, crc check failed");
			}

			image.addChunk(chunk);
		} while (len > 0 && PngChunk.IMAGE_TRAILER != chunk.type());
		return image;
	}

	public static PngImage read(final byte[] bytes) throws IOException {
		return read(new ByteArrayInputStream(bytes));
	}

	public static PngImage read(final Path path) throws IOException {
		try (final InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
			return read(input);
		}
	}

	/** */
	public PngImage() {
	}

	public List<PngChunk> chunks() {
		return this.chunks;
	}

	public long width() {
		return this.width;
	}

	public long height() {
		return this.height;
	}

	public long length() {
		return this.length;
	}

	public short getBitDepth() { return this.bitDepth; }
	public short getColorType() { return this.colorType; }

	public short getInterlace() { return this.interlace; }
	public void setInterlace(short interlace) { this.interlace = interlace; }
	public PngChunk getPalette() { return palette; }

	/** */
	public File export(String fileName, byte[] bytes) throws IOException {
		File out = new File(fileName);
		writeFileOutputStream(out, bytes);
		return out;
	}

	/** */
	FileOutputStream writeFileOutputStream(File out, byte[] bytes) throws IOException {
		FileOutputStream outs = null;
		try {
			outs = new FileOutputStream(out);
			outs.write(bytes);
		} finally {
			if (outs != null) {
				outs.close();
			}
		}

		return outs;
	}

	/** */
	public DataOutputStream writeDataOutputStream(OutputStream output) throws IOException {
		DataOutputStream outs = new DataOutputStream(output);
		outs.writeLong(PngImage.SIGNATURE);

		for (PngChunk chunk : chunks) {
			outs.writeInt(chunk.length());
			outs.writeInt(chunk.type());
			outs.write(chunk.data());
			outs.writeInt((int) chunk.crc());
		}
		outs.close();

		return outs;
	}

	/** */
	public void addChunk(PngChunk chunk) {
		switch (chunk.type()) {
			case PngChunk.IMAGE_HEADER:
				this.width = chunk.getWidth();
				this.height = chunk.getHeight();
				this.bitDepth = chunk.getBitDepth();
				this.colorType = chunk.getColorType();
				this.interlace = chunk.getInterlace();
				break;

			case PngChunk.PALETTE:
				this.palette = chunk;
				break;
		}

		this.chunks.add(chunk);
	}

	/** */
	public byte[] getImageData() {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();

			// Write all the IDAT data
			for (PngChunk chunk : chunks) {
				if (chunk.type() == PngChunk.IMAGE_DATA) {
					out.write(chunk.data());
				}
			}
			return out.toByteArray();
		} catch (IOException e) {
			System.out.println("Couldn't get image data: " + e);
		}
		return null;
	}

	/** */
	public int getSampleBitCount() {
		this.imageType = (this.imageType == null) ? PngImageType.forColorType(this.colorType) : this.imageType;
		return this.imageType.channelCount() * this.bitDepth;
	}

	/* */
	private byte[] getChunkData(InputStream ins, int length) throws PngException {
		byte[] data = new byte[length];
		try {
			int actual = ins.read(data);
			if (actual < length) {
				throw new PngException(String.format("Expected %d bytes but got %d", length, actual));
			}
		} catch (IOException e) {
			throw new PngException("Error reading chunk data", e);
		}

		return data;
	}

	/* */
	private long getChunkCrc(DataInputStream ins) throws IOException {
		int i = ins.readInt();
		long crc = i & 0x00000000ffffffffL; // Make it unsigned.
		return crc;
	}
}
