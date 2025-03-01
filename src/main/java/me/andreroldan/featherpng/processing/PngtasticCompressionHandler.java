package me.andreroldan.featherpng.processing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Implements PNG compression and decompression
 *
 * @author rayvanderborght
 */
public class PngtasticCompressionHandler implements PngCompressionHandler {
	private static final List<Integer> COMPRESSION_STRATEGIES = Arrays.asList(Deflater.DEFAULT_STRATEGY, Deflater.FILTERED, Deflater.HUFFMAN_ONLY);

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte[] deflate(PngByteArrayOutputStream inflatedImageData, Integer compressionLevel, boolean concurrent) throws IOException {
		final List<byte[]> results = (concurrent)
				? deflateImageDataConcurrently(inflatedImageData, compressionLevel)
				: deflateImageDataSerially(inflatedImageData, compressionLevel, Deflater.DEFAULT_STRATEGY);

		byte[] result = null;
		for (int i = 0; i < results.size(); i++) {
			final byte[] data = results.get(i);
			if (result == null || (data.length < result.length)) {
				result = data;
			}
		}

		return result;
	}

	@Override
	public String encodeBytes(byte[] bytes) {
		return Base64.encodeBytes(bytes);
	}

	/*
	 * Do the work of deflating (compressing) the image data with the
	 * different compression strategies in separate threads to take
	 * advantage of multiple core architectures.
	 */
	private List<byte[]> deflateImageDataConcurrently(final PngByteArrayOutputStream inflatedImageData, final Integer compressionLevel) {
		final Collection<byte[]> results = new ConcurrentLinkedQueue<>();

		final Collection<Callable<Object>> tasks = new ArrayList<>();
		for (final int strategy : COMPRESSION_STRATEGIES) {
			tasks.add(Executors.callable(new Runnable() {
				@Override
				public void run() {
					try {
						results.add(PngtasticCompressionHandler.this.deflateImageData(inflatedImageData, strategy, compressionLevel));
					} catch (Throwable e) {
						System.err.println("Uncaught Exception: " + e.getMessage());
						e.printStackTrace();
					}
				}
			}));
		}

		final ExecutorService compressionThreadPool = Executors.newFixedThreadPool(COMPRESSION_STRATEGIES.size());
		try {
			compressionThreadPool.invokeAll(tasks);
		} catch (InterruptedException ignored) {
		} finally {
			compressionThreadPool.shutdown();
		}

		return new ArrayList<>(results);
	}

	/* */
	private List<byte[]> deflateImageDataSerially(PngByteArrayOutputStream inflatedImageData, Integer compressionLevel, Integer compressionStrategy) {
		final List<byte[]> results = new ArrayList<>();

		final List<Integer> strategies = (compressionStrategy == null) ? COMPRESSION_STRATEGIES
				: Collections.singletonList(compressionStrategy);

		for (final int strategy : strategies) {
			try {
				results.add(PngtasticCompressionHandler.this.deflateImageData(inflatedImageData, strategy, compressionLevel));
			} catch (Throwable e) {
				System.err.println("Uncaught Exception: " + e.getMessage());
				e.printStackTrace();
			}
		}

		return results;
	}

	/* */
	private byte[] deflateImageData(PngByteArrayOutputStream inflatedImageData, int strategy, Integer compressionLevel) throws IOException {
		byte[] result = null;
		int bestCompression = Deflater.BEST_COMPRESSION;

		if (compressionLevel == null || compressionLevel > Deflater.BEST_COMPRESSION || compressionLevel < Deflater.NO_COMPRESSION) {
			for (int compression = Deflater.BEST_COMPRESSION; compression > Deflater.NO_COMPRESSION; compression--) {
				final ByteArrayOutputStream deflatedOut = deflate(inflatedImageData, strategy, compression);

				if (result == null || (result.length > deflatedOut.size())) {
					result = deflatedOut.toByteArray();
					bestCompression = compression;
				}
			}
		} else {
			result = deflate(inflatedImageData, strategy, compressionLevel).toByteArray();
			bestCompression = compressionLevel;
		}

		return result;
	}

	/* */
	private ByteArrayOutputStream deflate(PngByteArrayOutputStream inflatedImageData, int strategy, int compression) throws IOException {
		final ByteArrayOutputStream deflatedOut = new ByteArrayOutputStream();
		final Deflater deflater = new Deflater(compression);
		deflater.setStrategy(strategy);

		try {
			final DeflaterOutputStream stream = new DeflaterOutputStream(deflatedOut, deflater);
			stream.write(inflatedImageData.get(), 0, inflatedImageData.len());
			stream.close();
		} finally {
			deflater.end();
		}

		return deflatedOut;
	}
}
