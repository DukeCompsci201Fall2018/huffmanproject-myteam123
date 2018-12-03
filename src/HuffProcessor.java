import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);
		
		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
	}
	
	private void writeCompressedBits(String[] codings, BitInputStream input, BitOutputStream output) {
		while (true) {
		       int bits = input.readBits(BITS_PER_WORD);
		       if (bits == -1) {
		    	   String code = codings[PSEUDO_EOF];
	               output.writeBits(code.length(), Integer.parseInt(code,2));
		    	   break;
		       }
		           
               String code = codings[bits];
               output.writeBits(
            		   code.length(),
            		   Integer.parseInt(code,2));
		   }
	}
	
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
	    encodings = codingHelper(root,"", encodings);
	    return encodings;
	}
	
	private String[] codingHelper(HuffNode node, String path, String[] encodings ) {
		if (node == null) return encodings;
		if (node.myLeft==null && node.myRight==null) {
	        encodings[node.myValue] = path;
	        return encodings;
		}
		encodings = codingHelper(node.myLeft, path+"0", encodings);
		return codingHelper(node.myRight, path+"1", encodings);
	}
	
	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();


		for(int index = 0; index < counts.length; index++) {
			if(counts[index] > 0)
				pq.add(new HuffNode(index, counts[index],null,null));
		}

		while (pq.size() > 1) {
		    HuffNode left = pq.remove();
		    HuffNode right = pq.remove();
		    // create new HuffNode t with weight from
		    // left.weight+right.weight and left, right subtrees
		    HuffNode t = new HuffNode(left.myValue+right.myValue, left.myWeight+right.myWeight, left, right);
		    pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}
	
	private int[] readForCounts(BitInputStream in) {
		int[] returnable = new int[ALPH_SIZE + 1];
		Arrays.fill(returnable, 0);
		returnable [PSEUDO_EOF] = 1;
		while (true) {
       int readStuff = in.readBits(BITS_PER_WORD);
       if (readStuff == -1) {
           break;
       }
       else { 
           if (readStuff == PSEUDO_EOF) 
               break;   // out of loop
           else {
               returnable[readStuff]++;
           }
       }
		}
		return returnable;
	}
	
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){

		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("");
		}
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();
	}
	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if (bit == -1) throw new HuffException("");
		if (bit == 0) {
		    HuffNode left = readTreeHeader(in);
		    HuffNode right = readTreeHeader(in);
		    return new HuffNode(0, 0, left, right);
		}
		else {
		    int value = in.readBits(BITS_PER_WORD+1);
		    return new HuffNode(value, 0, null,null);
		}
	}
	private void writeHeader(HuffNode root, BitOutputStream out) {
		if (root.myLeft == null && root.myRight == null) {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD+1, root.myValue);
		} else {
			out.writeBits(1, 0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
	}
	private void readCompressedBits(HuffNode root, BitInputStream input, BitOutputStream out) {
		HuffNode current = root; 
	   while (true) {
	       int bits = input.readBits(1);
	       if (bits == -1) {
	           throw new HuffException("bad input, no PSEUDO_EOF");
	       }
	       else { 
	           if (bits == 0) current = current.myLeft;
	      else current = current.myRight;

	           if (current.myLeft == null && current.myRight == null) {
	               if (current.myValue == PSEUDO_EOF) 
	                   break;   // out of loop
	               else {
	                   out.writeBits(BITS_PER_WORD, current.myValue);
	                   current = root; // start back after leaf
	               }
	           }
	       }
	   }
	}
}