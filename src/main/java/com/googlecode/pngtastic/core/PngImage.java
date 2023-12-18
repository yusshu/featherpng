package com.googlecode.pngtastic.core;

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
	public static final long SIGNATURE = 0x89504e470d0a1a0aL;

	private String fileName;
	private final List<PngChunk> chunks = new ArrayList<>();

	private long width;
	private long height;

	private short bitDepth;
	private short colorType;
	private short interlace;

	private PngChunk palette;
	private PngImageType imageType;

	public static PngImage read(final InputStream ins) throws IOException {
		PngImage image = new PngImage();
		DataInputStream dis = new DataInputStream(ins);
		readSignature(dis);

		int length;
		PngChunk chunk;

		do {
			length = image.getChunkLength(dis);

			byte[] type = image.getChunkType(dis);
			byte[] data = image.getChunkData(dis, length);
			long crc = image.getChunkCrc(dis);

			chunk = new PngChunk(type, data);

			if (!chunk.verifyCRC(crc)) {
				throw new PngException("Corrupted file, crc check failed");
			}

			image.addChunk(chunk);
		} while (length > 0 && !PngChunk.IMAGE_TRAILER.equals(chunk.getTypeString()));
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

	public String getFileName() { return this.fileName; }

	public List<PngChunk> getChunks() { return this.chunks; }

	public long getWidth() { return this.width; }
	public long getHeight() { return this.height; }

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
			outs.writeInt(chunk.getLength());
			outs.write(chunk.getType());
			outs.write(chunk.getData());
			int i = (int)chunk.getCRC();
			outs.writeInt(i);
		}
		outs.close();

		return outs;
	}

	/** */
	public void addChunk(PngChunk chunk) {
		switch (chunk.getTypeString()) {
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
				if (chunk.getTypeString().equals(PngChunk.IMAGE_DATA)) {
					out.write(chunk.getData());
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
	private int getChunkLength(DataInputStream ins) throws IOException {
		return ins.readInt();
	}

	/* */
	private byte[] getChunkType(InputStream ins) throws PngException {
		return getChunkData(ins, 4);
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

	/* */
	private static void readSignature(DataInputStream ins) throws PngException, IOException {
		long signature = ins.readLong();
		if (signature != PngImage.SIGNATURE) {
			throw new PngException("Bad png signature");
		}
	}
}
