package me.andreroldan.featherpng;

import me.andreroldan.featherpng.processing.PngByteArrayOutputStream;
import me.andreroldan.featherpng.processing.ZopfliCompressionHandler;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Optimizes PNG images for smallest possible filesize.
 *
 * @author rayvanderborght
 */
public final class PngOptimizer extends PngProcessor {
	private boolean generateDataUriCss = false;
	private final List<OptimizerResult> results = new ArrayList<>();

	public List<OptimizerResult> getResults() {
		return results;
	}

	public void setGenerateDataUriCss(boolean generateDataUriCss) {
		this.generateDataUriCss = generateDataUriCss;
	}

	/** */
	public PngImage optimize(PngImage image) throws IOException {
		return optimize(image, false, null);
	}

	/** */
	public PngImage optimize(PngImage image, boolean removeGamma, Integer compressionLevel) throws IOException {
		// FIXME: support low bit depth interlaced images
		if (image.getInterlace() == 1 && image.getSampleBitCount() < 8) {
			return image;
		}

		final PngImage result = new PngImage();
		result.setInterlace((short) 0);

		final Iterator<PngChunk> itChunks = image.chunks().iterator();
		PngChunk chunk = processHeadChunks(result, removeGamma, itChunks);

		// collect image data chunks
		final PngByteArrayOutputStream inflatedImageData = getInflatedImageData(chunk, itChunks);

		final int scanlineLength = (int)(Math.ceil(image.width() * image.getSampleBitCount() / 8F)) + 1;

		final List<byte[]> originalScanlines = (image.getInterlace() == 1)
				? pngInterlaceHandler.deInterlace((int) image.width(), (int) image.height(), image.getSampleBitCount(), inflatedImageData)
				: getScanlines(inflatedImageData, image.getSampleBitCount(), scanlineLength, image.height());

		// TODO: use this for bit depth reduction
//		Map<PngPixel, Integer> colors = getColors(image, originalScanlines, 32);

		// apply each type of filtering
		final Map<PngFilterType, List<byte[]>> filteredScanlines = new HashMap<>();
		for (PngFilterType filterType : PngFilterType.standardValues()) {
			final List<byte[]> scanlines = copyScanlines(originalScanlines);
			pngFilterHandler.applyFiltering(filterType, scanlines, image.getSampleBitCount());

			filteredScanlines.put(filterType, scanlines);
		}

		// pick the filter that compresses best
		PngFilterType bestFilterType = null;
		byte[] deflatedImageData = null;
		for (Entry<PngFilterType, List<byte[]>> entry : filteredScanlines.entrySet()) {
			final byte[] imageResult = pngCompressionHandler.deflate(serialize(entry.getValue()), compressionLevel, true);
			if (deflatedImageData == null || imageResult.length < deflatedImageData.length) {
				deflatedImageData = imageResult;
				bestFilterType = entry.getKey();
			}
		}

		// see if adaptive filtering results in even better compression
		final List<byte[]> scanlines = copyScanlines(originalScanlines);
		pngFilterHandler.applyAdaptiveFiltering(inflatedImageData, scanlines, filteredScanlines, image.getSampleBitCount());

		final byte[] adaptiveImageData = pngCompressionHandler.deflate(inflatedImageData, compressionLevel, true);

		if (deflatedImageData == null || adaptiveImageData.length < deflatedImageData.length) {
			deflatedImageData = adaptiveImageData;
			bestFilterType = PngFilterType.ADAPTIVE;
		}

		final PngChunk imageChunk = new PngChunk(PngChunk.IMAGE_DATA, deflatedImageData);
		result.addChunk(imageChunk);

		// finish it
		while (chunk != null) {
			if (chunk.isCritical() && chunk.type() != PngChunk.IMAGE_DATA) {
				ByteArrayOutputStream bytes = new ByteArrayOutputStream(chunk.length());
				DataOutputStream data = new DataOutputStream(bytes);

				data.write(chunk.data());
				data.close();

				PngChunk newChunk = new PngChunk(chunk.type(), bytes.toByteArray());
				result.addChunk(newChunk);
			}
			chunk = itChunks.hasNext() ? itChunks.next() : null;
		}

		// make sure we have the IEND chunk
		final List<PngChunk> chunks = result.chunks();
		if (chunks != null && chunks.get(chunks.size() - 1).type() != PngChunk.IMAGE_TRAILER) {
			result.addChunk(new PngChunk(PngChunk.IMAGE_TRAILER, new byte[] { }));
		}

		return result;
	}

	/* */
	private List<byte[]> copyScanlines(List<byte[]> original) {
		final List<byte[]> copy = new ArrayList<>(original.size());
		for (byte[] scanline : original) {
			copy.add(scanline.clone());
		}

		return copy;
	}

	/* */
	private PngByteArrayOutputStream serialize(List<byte[]> scanlines) {
		final int scanlineLength = scanlines.get(0).length;
		final byte[] imageData = new byte[scanlineLength * scanlines.size()];
		for (int i = 0; i < scanlines.size(); i++) {
			final int offset = i * scanlineLength;
			final byte[] scanline = scanlines.get(i);
			System.arraycopy(scanline, 0, imageData, offset, scanlineLength);
		}

		return new PngByteArrayOutputStream(imageData);
	}

	/**
	 * Holds info about an image file optimization
	 */
	public static class OptimizerResult {
		private long originalFileSize;
		public long getOriginalFileSize() { return originalFileSize; }

		private long optimizedFileSize;
		public long getOptimizedFileSize() { return optimizedFileSize; }

		private String fileName;
		private long width;
		private long height;
		private String dataUri;

		public OptimizerResult(String fileName, long originalFileSize, long optimizedFileSize, long width, long height, String dataUri) {
			this.originalFileSize = originalFileSize;
			this.optimizedFileSize = optimizedFileSize;
			this.fileName = fileName;
			this.width = width;
			this.height = height;
			this.dataUri = dataUri;
		}
	}

	/**
	 * Get the number of bytes saved in all images processed so far
	 *
	 * @return The number of bytes saved
	 */
	public long getTotalSavings() {
		long totalSavings = 0;
		for (OptimizerResult result : results) {
			totalSavings += (result.getOriginalFileSize() - result.getOptimizedFileSize());
		}

		return totalSavings;
	}

	/**
	 * Get the css containing data uris of the images processed by the optimizer
	 */
	public void generateDataUriCss(String dir) throws IOException {
		final String path = (dir == null) ? "" : dir + "/";
		final PrintWriter out = new PrintWriter(path + "DataUriCss.html");

		try {
			out.append("<html>\n<head>\n\t<style>");

			for (OptimizerResult result : results) {
				final String name = result.fileName.replaceAll("[^A-Za-z0-9]", "_");
				out.append('#').append(name).append(" {\n")
						.append("\tbackground: url(\"data:image/png;base64,")
						.append(result.dataUri).append("\") no-repeat left top;\n")
						.append("\twidth: ").append(String.valueOf(result.width)).append("px;\n")
						.append("\theight: ").append(String.valueOf(result.height)).append("px;\n")
						.append("}\n");
			}
			out.append("\t</style>\n</head>\n<body>\n");

			for (OptimizerResult result : results) {
				final String name = result.fileName.replaceAll("[^A-Za-z0-9]", "_");
				out.append("\t<div id=\"").append(name).append("\"></div>\n");
			}

			out.append("</body>\n</html>");
		} finally {
			if (out != null) {
				out.close();
			}
		}
	}

	private byte[] getFileBytes(File originalFile, long originalFileSize) throws IOException {
		final ByteBuffer buffer = ByteBuffer.allocate((int) originalFileSize);
		FileInputStream ins = null;
		try {
			ins = new FileInputStream(originalFile);
			ins.getChannel().read(buffer);
		} finally {
			if (ins != null) {
				ins.close();
			}
		}
		return buffer.array();
	}

	public void setCompressor(String compressor, Integer iterations) {
		if ("zopfli".equals(compressor)) {
			if (iterations != null) {
				pngCompressionHandler = new ZopfliCompressionHandler(iterations);
			} else {
				pngCompressionHandler = new ZopfliCompressionHandler();
			}
		}
	}
}
