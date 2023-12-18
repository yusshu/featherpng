package me.andreroldan.featherpng;

import java.util.zip.CRC32;

/**
 * Represents a PNG chunk
 *
 * @author rayvanderborght
 */
public final class PngChunk {
	/* critical chunks */
	public static final int IMAGE_HEADER    = 0x49484452; // IHDR
	public static final int PALETTE			= 0x504C5445; // PLTE
	public static final int IMAGE_DATA		= 0x49444154; // IDAT
	public static final int IMAGE_TRAILER	= 0x49454e44; // IEND

	/* ancilliary chunks */
	public static final int TRANSPARENCY					= 0x74524e53; // tRNS
	public static final int COLOR_SPACE_INFO				= 0x6348524d; // cHRM
	public static final int IMAGE_GAMA					= 0x67414d41; // gAMA
	public static final int EMBEDDED_ICCP_PROFILE		= 0x69434350; // iCCP
	public static final int SIGNIFICANT_BITS				= 0x73424954; // sBIT
	public static final int STANDARD_RGB					= 0x73524742; // sRGB
	public static final int TEXTUAL_DATA					= 0x74455874; // tEXt
	public static final int COMPRESSED_TEXTUAL_DATA		= 0x7a545874; // zTXt
	public static final int INTERNATIONAL_TEXTUAL_DATA	= 0x69545874; // iTXt
	public static final int BACKGROUND_COLOR				= 0x624b4744; // bKGD
	public static final int IMAGE_HISTOGRAM				= 0x68495354; // hIST
	public static final int PHYSICAL_PIXEL_DIMENSIONS	= 0x70485973; // pHYs
	public static final int SUGGESTED_PALETTE			= 0x73504c54; // sPLT
	public static final int IMAGE_LAST_MODIFICATION_TIME	= 0x74494d45; // tIME

	private final int type;
	private final byte[] data;

	/** */
	public PngChunk(int type, byte[] data) {
		this.type = type;
		this.data = data;
	}

	/**
	 * Returns this PNG chunk type name, which is a four-letter
	 * ASCII string. Mostly human-readable.
	 *
	 * @return the type name of this PNG chunk
	 */
	public String typeName() {
		final char c1 = (char) ((this.type >> 24) & 0xff);
		final char c2 = (char) ((this.type >> 16) & 0xff);
		final char c3 = (char) ((this.type >> 8) & 0xff);
		final char c4 = (char) (this.type & 0xff);
		return new String(new char[] { c1, c2, c3, c4 });
	}

	/**
	 * Returns this PNG chunk type, which is one of the
	 * static constants in this class.
	 *
	 * <p>Example: {@link #IMAGE_HEADER}, {@link #IMAGE_DATA},
	 * {@link #IMAGE_TRAILER}, etc.</p>
	 *
	 * @return the type of this PNG chunk
	 */
	public int type() {
		return this.type;
	}

	/** */
	public byte[] data() {
		return this.data;
	}

	/** */
	public int length() {
		return this.data.length;
	}

	//#region IHDR properties
	/**
	 * Reads the 'width' property from this PNG chunk. It's
	 * expected that this PNG chunk is a IHDR chunk.
	 *
	 * <p>The returned width is the image width in pixels,
	 * zero is an invalid value.</p>
	 *
	 * @return the width of the image in pixels
	 */
	public long readWidth() {
		return this.getUnsignedInt(0);
	}

	/**
	 * Reads the 'height' property from this PNG chunk. It's
	 * expected that this PNG chunk is a IHDR chunk.
	 *
	 * <p>The returned height is the image height in pixels,
	 * zero is an invalid value.</p>
	 *
	 * @return the height of the image in pixels
	 */
	public long readHeight() {
		return this.getUnsignedInt(4);
	}

	/**
	 * Reads the bit depth for this image, which gives the number
	 * of bits per sample or per palette index (not per pixel).
	 *
	 * <p>Valid values are 1, 2, 4, 8 and 16, although not all values
	 * are allowed for all color types.</p>
	 *
	 * @return the bit depth of this image
	 */
	public short readBitDepth() {
		return this.getUnsignedByte(8);
	}

	/**
	 * Reads the color type for this image, which defines the PNG
	 * image type.
	 *
	 * <p>Valid values are 0, 2, 3, 4 and 6.</p>
	 *
	 * @return the color type of this image
	 */
	public short readColorType() {
		return this.getUnsignedByte(9);
	}

	/**
	 * Reads the compression method for this image, indicates the
	 * method used to compress the image data. Only compression
	 * method 0 (deflate/inflate compression with a sliding
	 * window of at most 32768 bytes) is defined in the PNG
	 * specification.
	 *
	 * @return the compression method of this image
	 */
	public short readCompression() {
		return this.getUnsignedByte(10);
	}

	/**
	 * Reads the filter method for this image, indicates the
	 * preprocessing method applied to the image data before
	 * compression.
	 *
	 * @return the filter method of this image
	 */
	public short readFilter() {
		return this.getUnsignedByte(11);
	}

	/**
	 * Reads the interlace method for this image, indicates the
	 * transmission order of the image data.
	 *
	 * @return the interlace method of this image
	 */
	public short readInterlace() {
		return this.getUnsignedByte(12);
	}

	/**
	 * Writes the interlace method for this image, indicates the
	 * transmission order of the image data.
	 *
	 * @param interlace the interlace method of this image
	 */
	public void writeInterlace(final byte interlace) {
		this.data[12] = interlace;
	}
	//#endregion IHDR properties

	/** */
	public long getUnsignedInt(int offset) {
		long value = 0;
		for (int i = 0; i < 4; i++) {
			value += (this.data[offset + i] & 0xff) << ((3 - i) * 8);
		}

		return value;
	}

	/** */
	public short getUnsignedByte(int offset) {
		return (short) (this.data[offset] & 0x00ff);
	}

	/** */
	public boolean isCritical() {
		return type == IMAGE_HEADER || type == PALETTE || type == IMAGE_DATA || type == IMAGE_TRAILER;
	}

	/** */
	public boolean isRequired() {
		return this.isCritical() || type == TRANSPARENCY || type == IMAGE_GAMA || type == COLOR_SPACE_INFO;
	}

	/** */
	public boolean verifyCRC(long crc) {
		return (this.crc() == crc);
	}

	/** */
	public long crc() {
		CRC32 crc32 = new CRC32();
		// update with the 4 bytes of type
		crc32.update(this.type >> 24);
		crc32.update(this.type >> 16);
		crc32.update(this.type >> 8);
		crc32.update(this.type);
		crc32.update(this.data);
		return crc32.getValue();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();
		result.append('[').append(this.typeName()).append(']').append('\n');
		if (type == PngChunk.IMAGE_HEADER) {
			result.append("Size:        ").append(this.readWidth()).append('x').append(this.readHeight()).append('\n');
			result.append("Bit depth:   ").append(this.readBitDepth()).append('\n');
			result.append("Image type:  ").append(this.readColorType()).append(" (").append(PngImageType.forColorType(this.readColorType())).append(")\n");
			result.append("Color type:  ").append(this.readColorType()).append('\n');
			result.append("Compression: ").append(this.readCompression()).append('\n');
			result.append("Filter:      ").append(this.readFilter()).append('\n');
			result.append("Interlace:   ").append(this.readInterlace());
		}
		if (type == PngChunk.TEXTUAL_DATA) {
			result.append("Text:        ").append(new String(this.data));
		}
		if (type == PngChunk.IMAGE_DATA) {
			result.append("Image Data:  ")
				.append("length=").append(this.length()).append(", data=");

//			for (byte b : this.data)
//				result.append(String.format("%x", b));

			result.append(", crc=").append(this.crc());
		}

		return result.toString();
	}
}
