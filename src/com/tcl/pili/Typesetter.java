package com.tcl.pili;

final class Typesetter implements MessageHandler {
	private static final int sBackgroundGrayThreshold = 240;
	
	public static Typesetter createForDevice(String device) {
		if (device.equals("nexus 5")) {
			return new Typesetter(15, 25, 3);
		}
		else {
			System.err.print("unknown device " + device + "\r\n");
			return null;
		}
	}
	
	private int mLinesOfPage;
	private int mWordsOfLine;
	private int mSpace;
	
	private int mTopMargin;
	private int mBottomMargin;
	private int mLeftMargin;
	private int mRightMargin;
	
	private Typesetter(int linesOfPage, int wordsOfLine, int space) {
		mLinesOfPage = linesOfPage;
		mWordsOfLine = wordsOfLine;
		mSpace = space;
		
		mTopMargin = 2 * wordHeight;
		mBottomMargin = 2 * wordHeight;
		mLeftMargin = 2 * wordWidth;
		mRightMargin = 2 * wordWidth;
	}
	
	public boolean handleMessage(Message msg) {
		if (msg.what == Message.MSG_MAKE_PAGES) {
			File plotImageFile = (File)msg.obj;
			BufferedImage plotImage = ImageIO.read(plotImageFile);
			
			BufferedImage enhancedPlotImage = preprocess(plotImage);
			if (Utils.DEBUG) {
				ImageIO.write(enhancedPlotImage, "jpeg", Utils.getSiblingFile(plotImageFile, "preprocessed.jpg"));
			}
			
			File pageDir = Utils.getSiblingFile(plotImageFile, "page");
			if (!pageDir.exist()) {
				pageDir.mkdir();
			}
			
			ArrayList<BufferedImage> pageList = typeset(enhancedPlotImage);
			for (int i = 0; i < pageList.size(); i++) {
				ImageIO.write(image, "png", Utils.getChildFile(pageDir, i + ".png"));
			}
			
			return true;
		}
		else {
			return false;
		}
	}
	
	private BufferedImage preprocess(BufferedImage src) {
		BufferedImage dst;
		
		dst = ImageProcess.crop(src, 30, 1);
		dst = ImageProcess.gray(dst);
		dst = ImageProcess.sharpen(dst);
		
		return dst;
	}
	
	private ArrayList<BufferedImage> typeset(BufferedImage image) {
		BufferedImage image = cropMargin(image);
		
		int wordWidth = predictWordWidth(image);
		int wordHeight = predictWordHeight(image);
		ArrayList<int[]> wordList = getWordInImage(image, wordWidth, wordHeight);
		
		boolean isNextSection = true;		
		do {
			BufferedImage pageImage = createPageImage(wordWidth, wordHeight);
			
			int lineCnt = mLinesOfPage;
			int yOffset = mTopMargin + 1;
			
			do {
				if (!isNextSection) {
					int[] word = wordList.get(0);
					if (word[0] == -1) {
						words.remove(0);
						newSection = true;
					}
				}
				
				int wordCnt = mWordsOfLine;
				int xOffset = mLeftMargin + 1;
				
				if (isNextSection) {
					//indent 2 blank word
					wordCnt -= 2;
					xOffset += (wordWidth + mSpace) * 2;
					
					isNextSection = false;
				}
				
				Graphics2D graphics = pageImage.createGraphics();
				do {
					int[] word = wordList.remove(0);
					if (word[0] == -1) {
						isNextSection = true;
						break;
					}
					
					BufferedImage wordImage = image.getSubimage(word[1], word[0], word[3] - word[1] + 1, word[2] - word[0] + 1);
					graphics.drawImage(wordImage, null, xOffset, yOffset);
					
					xOffset += wordWidth + mSpace;
				}
				while ((--wordCnt > 0) && !wordList.isEmpty());
				
				yOffset += wordHeight + space;
			}
			while ((--lineCnt > 0) && !wordList.isEmpty());
			
			pageList.add(pageImage);
		}
		while (!wordList.isEmpty());
		
		return pageList;
	}
	
	private BufferedImage cropMargin(BufferedImage image) {
		int top = 0;
		for (int i = 0; i < image.getHeight(); i++) {
			int wordPixels = 0;
			for (int j = 0; j < image.getWidth(); j++) {
				int gray = image.getRGB(j, i) & 0xff;
				if (gray < sBackgroundGrayThreshold) {
					wordPixels++;
				}
			}
			
			if (wordPixels > 0) {
				top = i;
				break;
			}
		}
		
		int bottom = image.getHeight() - 1;
		for (int i = image.getHeight() - 1; i >= 0; i--) {
			int wordPixels = 0;
			for (int j = 0; j < image.getWidth(); j++) {
				int gray = image.getRGB(j, i) & 0xff;
				if (gray < sBackgroundGrayThreshold) {
					wordPixels++;
				}
			}
			
			if (wordPixels > 0) {
				bottom = i;
				break;
			}
		}
		
		int left = 0;
		for (int i = 0; i < image.getWidth(); i++) {
			int wordPixels = 0;
			for (int j = 0; j < image.getHeight(); j++) {
				int gray = image.getRGB(i, j) & 0xff;
				if (gray < sBackgroundGrayThreshold) {
					wordPixels++;
				}
			}
			
			if (wordPixels > 0) {
				left = i;
				break;
			}
		}
		
		int right = image.getWidth() - 1;
		for (int i = image.getWidth() - 1; i >= 0; i--) {
			int wordPixels = 0;
			for (int j = 0; j < image.getHeight(); j++) {
				int gray = image.getRGB(i, j) & 0xff;
				if (gray < sBackgroundGrayThreshold) {
					wordPixels++;
				}
			}
			
			if (wordPixels > 0) {
				right = i;
				break;
			}
		}
		
		return image.getSubimage(left, top, right - left + 1, bottom - top + 1);
	}
	
	private int predictWordWidth(BufferedImage image) {
		Hashtable<Integer, Integer> widthStat = new Hashtable<Integer, Integer>();
		
		int xOffset = 0;
		while (true) {
			int[] column = getOneColumn(image, xOffset);
			if (column[0] == -1) {
				break;
			}
			
			int width = column[1] - column[0] + 1;
			int count = 1;
			if (widthStat.containsKey(width)) {
				count = widthStat.get(width) + 1;
			}
			widthStat.put(width, count);
			
			xOffset = column[1] + 1;
		}
		
		return getMode(widthStat);
	}
	
	private int[] getOneColumn(BufferedImage image, int pos) {
		int start = -1;
		int end = -1;
		
		for (int i = pos; i < image.getWidth(); i++) {
			int wordPixels = 0;
			for (int j = 0; j < image.getHeight(); j++) {
				int gray = image.getRGB(i, j) & 0xff;
				if (gray < sBackgroundGrayThreshold) {
					wordPixels++;
				}
			}
			
			if (wordPixels < image.getHeight() / 100) {
				if (start != -1) {
					break;
				}
			}
			else {
				if (start == -1) {
					start = i;
					end = start;
				}
				else {
					end += 1;
				}
			}
		}
		
		return new int[] {start, end};
	}
	
	private int predictWordHeight(BufferedImage image) {
		Hashtable<Integer, Integer> heightStat = new Hashtable<Integer, Integer>();
		
		int yOffset = 0;
		while (yOffset < image.getHeight()) {
			int[] line = getOneLine(image, yOffset);
			if (line[0] == -1) {
				break;
			}
			
			int height = line[1] - line[0] + 1;
			int count = 1;
			if (heightStat.containsKey(height)) {
				count = heightStat.get(height) + 1;
			}
			heightStat.put(height, count);
			
			yOffset = line[1] + 1;
		}
		
		return getMode(heightStat);
	}
	
	private int[] getOneLine(BufferedImage image, int pos) {
		int start = -1;
		int end = -1;
		
		for (int i = pos; i < image.getHeight(); i++) {
			int wordPixels = 0;
			for (int j = 0; j < image.getWidth(); j++) {
				int gray = image.getRGB(j, i) & 0xff;
				if (gray < sBackgroundGrayThreshold) {
					wordPixels++;
				}
			}
			
			if (wordPixels < image.getWidth() / 100) {
				if (start != -1) {
					break;
				}
			}
			else {
				if (start == -1) {
					start = i;
					end = start;
				}
				else {
					end += 1;
				}
			}
		}
		
		return new int[] {start, end};
	}
	
	private int getMode(Hashtable<Integer, Integer> stat) {
		Iterator<Map.Entry<Integer, Integer>> entryIterator = stat.entrySet().iterator();
		
		Map.Entry<Integer, Integer> maxCntEntry = null;
		while (entryIterator.hasNext()) {
			Map.Entry<Integer, Integer> entry = entryIterator.next();
			
			if ((maxCntEntry == null)
				|| (entry.getValue() > maxCntEntry.getValue())
				|| (entry.getValue() == maxCntEntry.getValue() && entry.getKey() > maxCntEntry.getKey())) {
				maxCntEntry = entry;
			}
		}
		
		return maxCntEntry.getKey();
	}
	
	private ArrayList<int[]> getWordInImage(BufferedImage image, int wordWidth, int wordHeight) {
		ArrayList<int[]> wordList = new ArrayList<int[]>();
		
		ArrayList<int[]> columns = getColumns(image, wordWidth);
		ArrayList<int[]> lines = getLines(image, wordHeight);
		for (int i = 0; i < lines.size(); i++) {
			int[] line = lines.get(i);
			
			if (i > 0) {
				int[] prevLine = lines.get(i - 1);
				if (line[0] - prevLine[1] > wordHeight) {
					wordList.add(new int[] {-1, -1, -1, -1});
				}
			}
			
			for (int j = 0; j < columns.size(); j++) {
				int[] column = columns.get(j);
				
				if (!isBlank(image, column, line)) {
					wordList.add(new int[] {line[0], column[0], line[1], column[1]});
				}
			}
		}
		
		return wordList;
	}
	
	private ArrayList<int[]> getLines(BufferedImage image, int wordHeight) {
		ArrayList<int[]> lines = new ArrayList<int[]>();
		
		int yOffset = 0;
		while (yOffset < image.getHeight()) {
			int[] line = getOneLine(image, yOffset);
			if (line[0] == -1) {
				break;
			}
			
			int height = line[1] - line[0] + 1;
			if (height < wordHeight / 4) {
				if (!lines.isEmpty()) {
					int[] prevLine = lines.get(lines.size() -1);
					
					int prevHeight = prevLine[1] - prevLine[0] + 1;
					if (prevHeight < wordHeight) {
						prevLine[1] = line[1];
					}
					else {
						lines.add(line);
					}					
				}
			}
			else {
				lines.add(line);
			}			
			
			yOffset = line[1] + 1;
		}
		
		return lines;
	}
	
	private ArrayList<int[]> getColumns(BufferedImage image, int wordWidth) {
		ArrayList<int[]> columns = new ArrayList<int[]>();
		
		int xOffset = 0;
		while (xOffset < image.getWidth()) {
			int[] column = getOneColumn(image, xOffset);
			if (column[0] == -1) {
				break;
			}
			
			int width = column[1] - column[0] + 1;
			if (width < wordWidth / 4) {
				if (!columns.isEmpty()) {
					int[] prevColumn = columns.get(columns.size() -1);
					
					int prevWidth = prevColumn[1] - prevColumn[0] + 1;
					if (prevWidth < wordWidth) {
						prevColumn[1] = column[1];
					}
					else {
						columns.add(column);
					}					
				}
			}
			else {
				columns.add(column);
			}			
			
			xOffset = column[1] + 1;
		}
		
		return columns;
	}
	
	private boolean isBlank(BufferedImage image, int[] column, int[] line) {
		int wordPixels = 0;
		for (int i = line[0]; i <= line[1]; i++) {
			for (int j = column[0]; j <= column[1]; j++) {
				int gray = image.getRGB(j, i) & 0xff;
				if (gray < sBackgroundGrayThreshold) {
					wordPixels++;
				}
			}
		}
		
		return wordPixels < 10;
	}
	
	private BufferedImage createPageImage(int wordWidth, int wordHeight) {
		int pageWidth = topMargin + (wordWidth * mWordsOfLine) + ((mWordsOfLine - 1) * space) + bottomMargin;
		int pageHeight = leftMargin + (wordHeight * mLinesOfPage) + ((mLinesOfPage - 1) * space) + rightMargin;
		BufferedImage pageImage = new BufferedImage(pageWidth, pageHeight, BufferedImage.TYPE_BYTE_GRAY);
		
		Graphics2D graphics = pageImage.createGraphics();
		graphics.setPaint(new Color(255, 255, 255));
		graphics.fillRect(0, 0, pageWidth, pageHeight);
		
		return pageImage;
	}
}
