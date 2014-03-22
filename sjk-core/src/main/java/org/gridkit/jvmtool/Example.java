package org.gridkit.jvmtool;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

public class Example {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		BASE64Encoder encoder = new BASE64Encoder();
		String value = "BR/0/0/2014-06-20/2014-06-24/262552,262641,262716,262802,262830,262857,269797,269864,282178,282193,282549,282577,284687,284931,285741,285837,286662,287440,287555,287623,288086,288619,289025,312486,312584,366846,375320,408852,409290,409452,411907,413083,414163,414910,423138,439671,449431,449606,456428,481561/2/BAR,BEW,COS,GTS,H,HA,HAR,HBE,HBS,HIL,IHG,KY,LOE,MEL,MIN,PAM,POS,RRE,STC,TOU,TRD,VEN,WYN/pt_BR";
		byte[] bytes = new byte[]{1, 2, 4,33,98, 43 ,43 ,23, 123, 109, 121,  18, 89, 92, 72, 98, 104, 32, 1, 2, 4,33,98, 43 ,43 ,23, 123, 109, 121,  18, 89, 92, 72, 98, 104, 32};
		bytes = value.getBytes();
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		GZIPOutputStream gzip = new GZIPOutputStream(out);
		gzip.write(value.getBytes());
		gzip.flush();
		
		
		byte[] compressed = out.toByteArray();
		System.out.println(value);
		System.out.println("Compressed: "+new String(compressed));
		
		
		String result = encoder.encode(compressed);
		System.out.println(bytes.length+" - "+compressed.length+" - "+result);
		
		
		BASE64Decoder base64Decoder = new BASE64Decoder();
		byte[] decodedZippedBytes = base64Decoder.decodeBuffer(new ByteArrayInputStream(result.getBytes()));
		System.out.println("Compressed after decoding: "+new String(decodedZippedBytes));

		GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(decodedZippedBytes));
//		BufferedInputStream bufferedInputStream = new BufferedInputStream(gzipInputStream);
		
		byte[] decompressed = new byte[404];
		gzipInputStream.read(decompressed, 0, 404);
		
		String string = new String(decompressed);
		
		System.out.println(string);

	}

}
