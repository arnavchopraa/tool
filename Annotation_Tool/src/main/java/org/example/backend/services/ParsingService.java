package org.example.backend.services;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.apache.pdfbox.text.TextPosition;
import org.example.backend.exceptions.PDFException;
import org.example.backend.models.AnnotationCode;
import org.example.backend.utils.*;
import org.springframework.stereotype.Service;

import java.awt.geom.Rectangle2D;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ParsingService {

    private final AnnotationCodeService annotationCodeService;

    private String removedCaptions = "";

    private Map<String, Boolean> captionsMap = new HashMap<>();
    private Map<String, String> annotationsMap = new HashMap<>();
    /**
     * This method creates a new instance of the ParsingService class
     *
     * @param annotationCodeService the service that provides codes
     */
    public ParsingService(AnnotationCodeService annotationCodeService) {
        this.annotationCodeService = annotationCodeService;
    }

    /**
     * This method parses a pdf file and returns the text, annotations and captions
     *
     * @param file the file that needs to be parsed
     * @return the parsed text
     * @throws PDFException if the file is not of type pdf
     */
    public PairUtils parsePDF(File file) throws PDFException {
        try {
            annotationsMap.clear();
            captionsMap.clear();
            PDDocument document = Loader.loadPDF(file);
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String text = pdfStripper.getText(document);
            text = preprocess(text);
            text = removeAbstract(text);

            String annotations = "";
            int pageIndex = 0;
            for (PDPage page : document.getPages()) {
                annotations = processPageAnnotations(page, annotations);
                text = processPageText(document, page, text, pageIndex);
                pageIndex++;
            }
            Iterator<String> iterator = annotationsMap.keySet().iterator();
            String captions = captionsMap.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining("@"));
            while (iterator.hasNext()) {
                String key = iterator.next();
                String value = annotationsMap.get(key);
                key = key.replaceAll("\r\n", "\n");
                int i = 0;
                List<String> captionsList = Arrays.asList(captions.split("@"));
                while ( i < captionsList.size() && !captionsList.get(i).contains(key)) {
                    i++;
                }
                if ( i < captionsList.size()) { // we found a match
                    annotations = annotations.replace(key.replaceAll("\n", "\r\n") + " - " + value, "");
                    captions = captions.replace(captionsList.get(i), captionsList.get(i) + " - " + value);
                }
                iterator.remove();
            }
            text = removeReferences(text, document);
            captions = captions.replaceAll("@", "\n");

            return new PairUtils(text, annotations, file.getName(), captions);
        } catch (IOException e) {
            throw new PDFException(file.getName());
        }
    }
    /**
     * This method processes the annotations on a page
     *
     * @param page the page on which the annotations are located
     * @param annotations the annotations that have been processed so far
     * @return the processed annotations
     * @throws IOException if the annotations can't be read
     */
    private String processPageAnnotations(PDPage page, String annotations) throws IOException {
        List<PDAnnotation> annotationList = page.getAnnotations();
        for (PDAnnotation a : annotationList) {
            annotations = processAnnotation(page, a, annotations);
        }
        return annotations;
    }
    /**
     * This method processes an annotation
     *
     * @param page the page on which the annotation is located
     * @param a the annotation that needs to be processed
     * @param annotations the annotations that have been processed so far
     * @return the processed annotations
     * @throws IOException if the annotation can't be read
     */
    private String processAnnotation(PDPage page, PDAnnotation a, String annotations) throws IOException {
        if (a.getSubtype().equals("Highlight")) {
            annotations = processHighlightAnnotation(page, a, annotations);
        } else if (a.getSubtype().equals("Text")) {
            annotations = processTextAnnotation(a, annotations);
        }
        return annotations;
    }
    /**
     * This method processes a highlight annotation
     *
     * @param page the page on which the annotation is located
     * @param a the annotation that needs to be processed
     * @param annotations the annotations that have been processed so far
     * @return the processed annotations
     * @throws IOException if the annotation can't be read
     */
    private String processHighlightAnnotation(PDPage page, PDAnnotation a, String annotations) throws IOException {
        //annotations = annotations + "\n" + getHighlightedText(a, page) + " - " + queryService.queryResults(a.getContents()) + "\n";
        if (!annotations.equals(""))
            annotations = annotations + "\n";
        annotationsMap.put(getHighlightedText(a, page), a.getContents());
        //annotations = annotations + getHighlightedText(a, page) + " - " + a.getContents() + "\n";
        if(a.getContents() == null)
            return annotations;
        AnnotationCode ac = annotationCodeService.getAnnotationCode(a.getContents());
        if(ac != null) {
            annotations = annotations + "\n" + getHighlightedText(a, page) + " - "
                    + preprocess(annotationCodeService.getAnnotationCode(a.getContents()).getCodeContent()) + "\n";
        } else {
            annotations = annotations + "\n" + getHighlightedText(a, page) + " - "
                    + preprocess(a.getContents()) + "\n";
        }
        return annotations;
    }
    /**
     * This method processes a text annotation
     *
     * @param a the annotation that needs to be processed
     * @param annotations the annotations that have been processed so far
     * @return the processed annotations
     */
    private String processTextAnnotation(PDAnnotation a, String annotations) {
        if (!annotations.equals(""))
            annotations = annotations + "\n";
        annotations = annotations + a.getContents() + "\n";
        return annotations;
    }
    /**
     * This method processes the text on a page
     *
     * @param document the document that contains the page
     * @param page the page that needs to be processed
     * @param text the text that needs to be processed
     * @param pageIndex the index of the page
     * @return the processed text
     * @throws IOException if the text can't be read
     */
    private String processPageText(PDDocument document, PDPage page, String text, int pageIndex) throws IOException {
        List<CoordPairs> map = parseWordCoordinates(document, page);
        TriplePair pair = averageDistance(map);

        List<Float> frontX = pair.getFrontX();
        List<Float> endX = pair.getEndX();

        List<Map.Entry<Float, Long>> frontXList = frontX.stream()
            .collect(Collectors.groupingBy(e -> e, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<Float, Long>comparingByValue().reversed())
            .toList();
        List<Map.Entry<Float, Long>> endXList = endX.stream()
            .collect(Collectors.groupingBy(e -> e, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<Float, Long>comparingByValue().reversed())
            .toList();
        PageDrawerUtils pdu = new PageDrawerUtils(page, pageIndex);
        pdu.processPage(page);
        List<PDFObject> images = pdu.getImages();
        List<Line> lines = mergeLines(pdu.getLines());
        List<PDFObject> tables = processLines(lines, pageIndex);
        if (frontX.isEmpty())
            frontX.add(0.0f);
        if (endX.isEmpty())
            endX.add(page.getMediaBox().getWidth());
        List<List<Float>> clustersFrontX = KMeans.clusterCoordinates(frontX);
        List<List<Float>> clustersEndX = KMeans.clusterCoordinates(endX);
        float colOneStart = Collections.min(clustersFrontX.get(0));
        float colTwoStart = Collections.min(clustersFrontX.get(1));
        float colOneEnd = Collections.max(clustersEndX.get(0));
        float colTwoEnd = Collections.max(clustersEndX.get(1));
        float columnThreshold = 50.0f;
        boolean isTwoColumns = (colTwoStart - colOneStart) > columnThreshold;
        text = removeTables(text, tables, page, colOneStart, colOneEnd, colTwoStart, colTwoEnd, isTwoColumns);
        for (PDFObject t : images) {
            text = removeTextUnderObject(t, page, colOneStart, colOneEnd, colTwoStart, colTwoEnd, text, isTwoColumns, null, null);
        }
        return text;
    }

    /**
     * Retrieves the highlighted text from an annotation
     *
     * @param a    the annotation from which to retrieve the highlighted text
     * @param page the page on which the annotation is situated
     * @return the highlighted text
     * @throws IOException if the text can't be read
     */
    public String getHighlightedText(PDAnnotation a, PDPage page) throws IOException {
        PDFTextStripperByArea stripperByArea = new PDFTextStripperByArea();

        COSArray quads = a.getCOSObject().getCOSArray(COSName.getPDFName("QuadPoints"));    //getting all the rectangles the annotation highlights
        String annot = "";

        int rectangle = 0;
        while (rectangle < quads.size()) {

            //getting the coordinates of the rectangle
            COSNumber ulx = (COSNumber) quads.get(rectangle);       //upper left x coordinate
            COSNumber uly = (COSNumber) quads.get(1 + rectangle);       //upper left y coordinate
            COSNumber urx = (COSNumber) quads.get(2 + rectangle);       //upper right x coordinate
            COSNumber ury = (COSNumber) quads.get(3 + rectangle);       //upper right y coordinate
            COSNumber llx = (COSNumber) quads.get(4 + rectangle);       //lower left x coordinate
            COSNumber lly = (COSNumber) quads.get(5 + rectangle);       //lower left y coordinate

            float xStart = ulx.floatValue() - 1;                    //x coordinate at the top left of the rectangle
            float yStart = uly.floatValue();                        //y coordinate at the top left of the rectangle
            float width = urx.floatValue() - llx.floatValue();      //width of rectangle
            float height = ury.floatValue() - lly.floatValue();     //height of the rectangle

            PDRectangle pageSize = page.getMediaBox();
            yStart = pageSize.getHeight() - yStart;             //aligning box with corresponding page

            Rectangle2D.Float box = new Rectangle2D.Float(xStart, yStart, width, height);   //the box from which we will parse text
            stripperByArea.addRegion("annotation", box);                          //set the region from which we will extract
            stripperByArea.extractRegions(page);                                            //set the page from which we will extract

            annot = annot + stripperByArea.getTextForRegion("annotation");

            rectangle = rectangle + 8;                                                      //moving to the next rectangle
        }

        //annot = annot.replace("\n", "");

        return annot;
    }

    /**
     * This method removes hyphenation from end of lines.
     *
     * @param text the string we want to remove unnecessary hyphenation from
     * @return the processed text
     */
    public String preprocess(String text) {
        int i = 0;
        StringBuilder sb = new StringBuilder();
        while (i < text.length()) {
            try {
                if (text.charAt(i) == '-' && text.charAt(i - 1) != ' ') {
                    if (text.charAt(i + 1) == '\r' && text.charAt(i + 2) == '\n') {
                        // skip over the indices containing EOL characters
                        i += 3;
                        while (text.charAt(i) != ' ') {
                            // check if the next characters are EOL such that to not search next space
                            if (text.charAt(i) == '\r') {
                                i++;
                                break;
                            }
                            sb.append(text.charAt(i));
                            i++;
                        }
                        // after the word is finished add EOL
                        sb.append("\r\n");
                    }
                    // hyphen is in the middle of the line
                    else {
                        sb.append(text.charAt(i));
                    }
                }
                // there is no hyphen
                else {
                    sb.append(text.charAt(i));
                }
                i++;
            }
            // if the hyphen goes past the last character of the file
            catch (IndexOutOfBoundsException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }

    /**
     * This method removes all content presented before the abstract
     *
     * @param text the text we want to remove the lines before the abstract from
     * @return processed text
     */
    public String removeAbstract(String text) {
        int index = text.indexOf("Abstract\r\n");
        // skip over the Abstract\r\n characters (Abstract\r\n is 10 characters)
        if (index != -1)
            return text.substring(index + 10);
        return text;
    }

    /**
     * Given a list of lines, separates tables
     *
     * @param lines list of identified lines
     * @param pageIndex the index of the page
     * @return list of identified table coordinates
     */
    public List<PDFObject> processLines(List<Line> lines, int pageIndex) {
        List<Line> horizontalLines = new ArrayList<>();
        List<Line> verticalLines = new ArrayList<>();
        List<PDFObject> tables = new ArrayList<>();
        Map<Line, Integer> correspondingTable = new HashMap<>();

        for (Line l : lines) {           //splitting vertical and horizontal lines
            if (l.isVertical())
                verticalLines.add(l);
            else
                horizontalLines.add(l);
        }

        for (Line horizontal : horizontalLines) {
            PDFObject table = new PDFObject(horizontal.getStartX(), horizontal.getStartY(), horizontal.getEndX(), horizontal.getEndY(), pageIndex);
            List<Line> addedLines = new ArrayList<>();
            int pos = -1;

            for (Line vertical : verticalLines) {
                if (horizontal.intersectsWith(vertical)) {
                    if (correspondingTable.containsKey(vertical)) {
                        pos = correspondingTable.get(vertical);
                        table = tables.get(pos);

                        table.combineTable(horizontal);         //what if we need to combine 2 tables?
                    } else {
                        table.combineTable(vertical);
                        addedLines.add(vertical);
                    }
                }
            }

            if (pos == -1) {
                tables.add(table);
                for (Line l : addedLines) {
                    correspondingTable.put(l, tables.size() - 1);
                }
            } else {
                for (Line l : addedLines) {
                    correspondingTable.put(l, pos);
                }
            }
        }
        return tables;
    }

    /**
     * Given a list of Lines, merge lines that are close to each other
     *
     * @param lines list of lines extracted
     * @return list of lines after executing the merges
     */
    public List<Line> mergeLines(List<Line> lines) {
        float error = 0.4f; // define error parameter; Observed error is always 0.398, leave room for precision errors

        Collections.sort(lines);
        boolean changes = true;
        while (changes) {
            changes = false;
            for (int i = 0; i < lines.size(); i++) {
                Line current = lines.get(i);
                for (int j = i + 1; j < lines.size(); j++) {
                    Line other = lines.get(j);
                    if (current.mergeWith(other, error)) {
                        changes = true;
                        lines.remove(j);
                    }
                }
            }
        }
        return lines;
    }

    /**
     * Remove the text present in the given tables from the specified text
     *
     * @param text   Text to remove from
     * @param tables Tables which contain the text that needs to be removed
     * @param page   Page in which the tables are located
     * @param colOneStart X coordinate of the start of the first column
     * @param colOneEnd X coordinate of the end of the first column
     * @param colTwoStart X coordinate of the start of the second column
     * @param colTwoEnd X coordinate of the end of the second column
     * @param isTwoColumn boolean value that tells us if the document has two columns
     * @return The initial text without the text in the tables
     * @throws IOException if the text can't be read
     */
    public String removeTables(String text, List<PDFObject> tables, PDPage page, float colOneStart, float colOneEnd,
        float colTwoStart, float colTwoEnd, boolean isTwoColumn) throws IOException {

        PDFTextStripperByArea stripperByArea = new PDFTextStripperByArea();
        for (PDFObject t : tables) {
            float xStart = t.getTopLeftX();
            float yStart = t.getBottomRightY();
            float width = t.getBottomRightX() - xStart;
            float height = t.getBottomRightY() - t.getTopLeftY();
            text = removeTextUnderObject(t, page, colOneStart, colOneEnd, colTwoStart, colTwoEnd,
                text, isTwoColumn, null, null);
            PDRectangle pageSize = page.getMediaBox();
            yStart = pageSize.getHeight() - yStart;

            Rectangle2D.Float box = new Rectangle2D.Float(xStart, yStart, width, height);
            stripperByArea.addRegion("table", box);
            stripperByArea.extractRegions(page);

            String remove = stripperByArea.getTextForRegion("table");

            text = text.replace(remove, "");
        }
        return text;
    }


    /**
     * Removes the references in the document from the given text
     *
     * @param text     Text that needs references removed
     * @param document Document which we are parsing
     * @return Updated text with references removed
     * @throws IOException When parsing the document fails
     */
    public String removeReferences(String text, PDDocument document) throws IOException {

        PDFTextStripperUtils ptsu = new PDFTextStripperUtils();
        ptsu.getText(document);

        String lastLine = findLastLineFromReferences(ptsu.getAllLines());

        if (lastLine == null)
            return text;

        String[] cut1 = text.split("\r\nReferences\r\n");

        if (cut1.length == 1)
            cut1 = text.split("\r\nreferences\r\n");

        if (cut1.length == 1)
            return text;

        String[] cut2 = cut1[1].split(lastLine);

        text = cut1[0] + "\r\n" + cut2[1];

        return text;
    }

    /**
     * Finds the text from the last line in the references subsection
     *
     * @param allLines All lines contained in the document
     * @return Text from the last line in the references
     */
    public String findLastLineFromReferences(List<List<List<TextPosition>>> allLines) {
        //first we need to find the "References" line

        List<String> referencesUnicode = new ArrayList<>(); //we will use this to compare it with each line
        referencesUnicode.add("R");
        referencesUnicode.add("e");
        referencesUnicode.add("f");
        referencesUnicode.add("e");
        referencesUnicode.add("r");
        referencesUnicode.add("e");
        referencesUnicode.add("n");
        referencesUnicode.add("c");
        referencesUnicode.add("e");
        referencesUnicode.add("s");

        int lineNumber = 0;
        boolean found = false;
        String font = null;
        float height = 0;


        while (lineNumber < allLines.size() && !found) {
            List<List<TextPosition>> line = allLines.get(lineNumber);

            if (line.size() > 1) {                             //if it's more than 1 word it can't be references
                lineNumber++;
                continue;
            }

            List<TextPosition> word = line.get(0);

            if (word.size() != referencesUnicode.size()) {   //if the word is not of the same size it can't be references
                lineNumber++;
                continue;
            }

            //if it doesn't start with r/R it can't be references
            //we check the first letter manually in case it is not capitalised
            if (!Objects.equals(word.get(0).toString(), "r") && !Objects.equals(word.get(0).toString(), "R")) {
                lineNumber++;
                continue;
            }

            int i = 1;

            while (i < word.size() && Objects.equals(word.get(i).toString(), referencesUnicode.get(i)))
                i++;

            if (i == word.size()) {                               //if we reached the end it's the same word
                found = true;
                font = word.get(0).getFont().getName();
                height = word.get(0).getHeightDir();

            }

            lineNumber++;
        }

        if (!found)              //there are no references
            return null;

        lineNumber = findNextSubsectionTitleLine(lineNumber, allLines, font, height);

        lineNumber--;

        String lastLine = "\r\n";
        List<List<TextPosition>> line = allLines.get(lineNumber);

        for (List<TextPosition> word : line) {
            for (TextPosition letter : word) {
                String currentFont = letter.getFont().getName();
                float currentHeight = letter.getHeightDir();

                if (!(currentFont.contains(font) && currentHeight == height))
                    lastLine = lastLine + letter;
            }
            lastLine = lastLine + " ";
        }

        lastLine = lastLine.trim();             //removing the last "_"
        return lastLine + "\r\n";
    }

    /**
     * Finds the line number of the next subsection title
     *
     * @param lineNumber Line number of the current subsection title
     * @param allLines   All lines in the document
     * @param font       The font of the title
     * @param height     The size of the title
     * @return Line number of the next subsection title
     */
    public int findNextSubsectionTitleLine(int lineNumber, List<List<List<TextPosition>>> allLines, String font, float height) {
        //finding the next subsection title

        boolean found = false;
        while (lineNumber < allLines.size() && !found) {
            List<List<TextPosition>> line = allLines.get(lineNumber);
            for (List<TextPosition> word : line)
                for (TextPosition letter : word) {
                    String currentFont = letter.getFont().getName();
                    float currentHeight = letter.getHeightDir();

                    //the letters have to be of the same font and size as "References" title
                    if (currentFont.contains(font) && currentHeight == height)
                        found = true;
                }

            lineNumber++;
        }

        return lineNumber;
    }

    /**
     * This method creates for each word a pair containing itself and it's coordinates in the page.
     *
     * @param doc the pdf file
     * @param page the page on which the words are located
     * @return a list of (coordinate, word) pairs
     * @throws IOException
     */

    public List<CoordPairs> parseWordCoordinates(PDDocument doc, PDPage page) throws IOException {
        PositionalTextUtils posTextStripper = new PositionalTextUtils();
        posTextStripper.setStartPage(doc.getPages().indexOf(page));
        posTextStripper.setEndPage(doc.getPages().indexOf(page) + 1);
        List<CoordPairs> chunks = posTextStripper.getTextBlocks(doc);
        return chunks;
    }

    /**
     * This method calculates the distance between lines of text in the document.
     *
     * @param wordMap list of (coordinate, word) pairs
     * @return list of float numbers representing distances between lines
     */
    public TriplePair averageDistance(List<CoordPairs> wordMap) {
        boolean first = true;
        Rectangle2D.Float prev = wordMap.get(0).getRect();
        List<Float> firstOfLineX = new ArrayList<>();
        List<Float> lastOfLineX = new ArrayList<>();
        List<Float> distancesY = new ArrayList<>();
        for(CoordPairs c : wordMap) {
            if(first) {
                prev = c.getRect();
                first = false;
            }
            else {
                float diff = c.getRect().y - prev.y;
                if(diff > 0) {
                    distancesY.add(diff);
                    firstOfLineX.add(c.getRect().x);
                    lastOfLineX.add(prev.x + prev.width);
                }
                prev = c.getRect();
            }
        }
        return new TriplePair(firstOfLineX, lastOfLineX, distancesY);
    }

    /**
     *
     * @param table list of page elements: images and tables
     * @param page the page on document were the element is
     * @param colOneStart X coordinate of the start of the first column
     * @param colOneEnd X coordinate of the end of the first column
     * @param colTwoStart X coordinate of the start of the second column
     * @param colTwoEnd X coordinate of the end of the second column
     * @param text the text from which we want to remove the text under the table
     * @param isTwoColumn boolean value that tells us if the document has two columns
     * @param stripperByArea Custom instance of stripper that extracts text from a specific area
     * @param stripper stripper that extracts text from a specific area
     * @return the text without the text under the table
     * @throws IOException if the text can't be read
     */
    public String removeTextUnderObject(PDFObject table, PDPage page, float colOneStart, float colOneEnd,
        float colTwoStart, float colTwoEnd, String text, boolean isTwoColumn,
        CustomPDFTextStripperByArea stripperByArea, PDFTextStripperByArea stripper) throws IOException {

        float xStart = table.getTopLeftX();
        float yStart = table.getTopLeftY();

        PDRectangle pageSize = page.getMediaBox();
        yStart = pageSize.getHeight() - yStart;

        Rectangle2D.Float box = null;
        if (!isTwoColumn) {
            box = new Rectangle2D.Float(colOneStart, yStart, colOneEnd - colOneStart, 300);
        } else if (xStart >= colTwoStart) {
            box = new Rectangle2D.Float(colTwoStart, yStart, colTwoEnd - colTwoStart, 300);
        } else if (table.getBottomRightX() <= colOneEnd) {
            box = new Rectangle2D.Float(colOneStart, yStart, colOneEnd - colOneStart, 300);
        } else {
            box = new Rectangle2D.Float(colOneStart, yStart, colTwoEnd - colOneStart, 300);
        }
        if (stripperByArea == null)
            stripperByArea = new CustomPDFTextStripperByArea();
        if (stripper == null)
            stripper = new PDFTextStripperByArea();
        stripperByArea.addRegion("table", box);
        stripper.addRegion("table", box);
        stripperByArea.extractRegions(page);
        stripper.extractRegions(page);
        List<Float> yCoordinates = new ArrayList<>(new HashSet<>(stripperByArea.getYCoordinates()));
        Collections.sort(yCoordinates);
        int index = 2;
        if (yCoordinates.size() < 2) {
            index = 1;
        } else {
            float distance = yCoordinates.get(0) - yCoordinates.get(1);
            //System.out.println("Distance=" + distance);
            distance = Math.abs(distance);
            if ( distance > 15.f) {
                index = 1;
            } else {
                while (index < yCoordinates.size() &&
                        Math.abs(yCoordinates.get(index) - yCoordinates.get(index - 1) - distance) < 2.f) {
                    index++;
                }
            }
        }
        int counter = index;
        String textUnderTable = stripper.getTextForRegion("table");
        //  System.out.println(textUnderTable);
        //System.out.println("table end");
        String captionPattern = "^(Figure|Table|Chart) \\d+[.:].*";
        Pattern pattern = Pattern.compile(captionPattern);
        Matcher matcher = pattern.matcher(textUnderTable);
        if ( matcher.find() ) {
            //System.out.println("Counter=" + counter);
            String lines[] = textUnderTable.split("\\r?\\n");
            int i = 0;
            String removedText = "";
            while (i < counter && i < lines.length) {
                removedText = removedText + lines[i] + "\n";
                text = text.replace(lines[i], "");
                i++;
            }
            captionsMap.put(removedText, true);
            removedCaptions += removedText;
            //System.out.println("Removed: " + removedText);
        }
        //System.out.println(stripperByArea.getTextForRegion("table"));
        return text;
    }
}

