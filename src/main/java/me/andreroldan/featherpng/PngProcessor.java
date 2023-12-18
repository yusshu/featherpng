package me.andreroldan.featherpng;

import me.andreroldan.featherpng.processing.PngByteArrayOutputStream;
import me.andreroldan.featherpng.processing.PngCompressionHandler;
import me.andreroldan.featherpng.processing.PngFilterHandler;
import me.andreroldan.featherpng.processing.PngInterlaceHandler;
import me.andreroldan.featherpng.processing.PngtasticCompressionHandler;
import me.andreroldan.featherpng.processing.PngtasticFilterHandler;
import me.andreroldan.featherpng.processing.PngtasticInterlaceHandler;

import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.InflaterInputStream;

/**
 * Base class for png image processing
 *
 * @author ray
 */
public abstract class PngProcessor {

	protected final PngFilterHandler pngFilterHandler;
	protected final PngInterlaceHandler pngInterlaceHandler;

	protected PngCompressionHandler pngCompressionHandler;

	protected PngProcessor() {
		this.pngFilterHandler = new PngtasticFilterHandler();
		this.pngInterlaceHandler = new PngtasticInterlaceHandler(pngFilterHandler);
		this.pngCompressionHandler = new PngtasticCompressionHandler();
	}

	protected PngByteArrayOutputStream getInflatedImageData(PngChunk chunk, Iterator<PngChunk> itChunks)
			throws IOException {

		final PngByteArrayOutputStream imageBytes = new PngByteArrayOutputStream(chunk == null ? 0 : chunk.length());
		try (final DataOutputStream imageData = new DataOutputStream(imageBytes)) {
			while (chunk != null) {
				if (chunk.type() == PngChunk.IMAGE_DATA) {
					imageData.write(chunk.data());
				} else {
					break;
				}
				chunk = itChunks.hasNext() ? itChunks.next() : null;
			}
			return inflate(imageBytes);
		}
	}

	/**
	 * Inflate (decompress) the compressed image data
	 *
	 * @param bytes A stream containing the compressed image data
	 * @return A byte array containing the uncompressed data
	 */
	public PngByteArrayOutputStream inflate(PngByteArrayOutputStream bytes) throws IOException {
		try (final PngByteArrayOutputStream inflatedOut = new PngByteArrayOutputStream();
		     final InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(bytes.get(), 0, bytes.len()))) {

			int readLength;
			final byte[] block = new byte[8192];

			while ((readLength = inflater.read(block)) != -1) {
				inflatedOut.write(block, 0, readLength);
			}
			return inflatedOut;
		}
	}

	protected List<byte[]> getScanlines(PngByteArrayOutputStream inflatedImageData, int sampleBitCount, int rowLength, long height) {
		final List<byte[]> rows = new ArrayList<>(Math.max((int) height, 0));
		byte[] previousRow = new byte[rowLength];

		for (int i = 0; i < height; i++) {
			final int offset = i * rowLength;
			final byte[] row = new byte[rowLength];
			System.arraycopy(inflatedImageData.get(), offset, row, 0, rowLength);
			try {
				pngFilterHandler.deFilter(row, previousRow, sampleBitCount);
				rows.add(row);
				previousRow = row.clone();
			} catch (PngException e) {
				System.err.println("Error: " + e.getMessage());
				e.printStackTrace();
			}
		}
		return rows;
	}

	protected PngChunk processHeadChunks(PngImage result, boolean removeGamma, Iterator<PngChunk> itChunks) throws IOException {
		PngChunk chunk = null;
		while (itChunks.hasNext()) {
			chunk = itChunks.next();
			if (chunk.type() == PngChunk.IMAGE_DATA) {
				break;
			}

			if (result != null && chunk.isRequired()) {
				if (removeGamma && chunk.type() == PngChunk.IMAGE_GAMA) {
					continue;
				}

				PngChunk newChunk = new PngChunk(chunk.type(), chunk.data().clone());
				if (chunk.type() == PngChunk.IMAGE_HEADER) {
					newChunk.writeInterlace((byte) 0);
				}
				result.addChunk(newChunk);
			}
		}
		return chunk;
	}

	/* */
	@SuppressWarnings("unused")
	protected void printData(byte[] inflatedImageData) {
		final StringBuilder result = new StringBuilder();
		for (byte b : inflatedImageData) {
			result.append(String.format("%2x|", b));
		}
		System.err.println(result);
	}
}
