package services;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.example.TestUtils;
import org.example.backend.exceptions.PDFException;
import org.example.backend.services.AnnotationCodeService;
import org.example.backend.services.ParsingService;
import org.example.backend.utils.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Matchers.any;

public class ParsingServiceTest {

    private final TestUtils testUtils = new TestUtils();
    private ParsingService ps;
    private AnnotationCodeService annotationCodeService;

    @BeforeEach
    public void setUp() {
        annotationCodeService = Mockito.mock(AnnotationCodeService.class);
        ps = new ParsingService(annotationCodeService);
    }

    @Test
    void mergeLinesTest() {

        Line l1 = new Line(0, 1, 2, 1);
        Line l2 = new Line(2.3f, 1, 5, 1);
        Line l3 = new Line(5.5f, 1, 5, 1);

        List<Line> lines = new ArrayList<>();
        lines.add(l1);
        lines.add(l2);
        lines.add(l3);

        Line ans1 = new Line(0, 1, 5, 1);
        List<Line> ans = new ArrayList<>();
        ans.add(ans1);
        ans.add(l3);

        assertEquals(ans, ps.mergeLines(lines));
    }

    @Test
    void processLinesTest() {

        Line l1 = new Line(0, 1, 4, 1);
        Line l2 = new Line(0, 1, 0, 5);

        Line l3 = new Line(10, 10, 15, 10);
        Line l4 = new Line(12, 10, 12, 15);
        Line l5 = new Line(10, 13, 20, 13);
        Line l6 = new Line(20, 13, 20, 15);

        List<Line> lines = new ArrayList<>();
        lines.add(l1);
        lines.add(l2);
        lines.add(l3);
        lines.add(l4);
        lines.add(l5);
        lines.add(l6);

        PDFObject PDFObject1 = new PDFObject(0, 1, 4, 5, 0);
        PDFObject PDFObject2 = new PDFObject(10, 10, 20, 15, 0);
        List<PDFObject> ans = new ArrayList<>();
        ans.add(PDFObject1);
        ans.add(PDFObject2);

        assertEquals(ans, ps.processLines(lines, 0));
    }

    @Test
    public void testParsePdfNoAnnotations() {
        String text = "This is a PDF file";
        try {
            File pdf = testUtils.convertPDFtoFile(testUtils.generatePDF(text));
            PairUtils pair = ps.parsePDF(pdf);
            String res = pair.getText();
            res = res.replaceAll("\r", "");
            res = res.replaceAll("\n", "");
            assertEquals(text, res);
            assertEquals("", pair.getAnnotations());
            assertEquals(pair.removeFileExtension(pdf.getName()), pair.getFileName());
            pdf.deleteOnExit();
        } catch (IOException | PDFException e) {
            throw new RuntimeException("Test failed - Could not generate PDF");
        }
    }

    @Test
    public void testParsePdfAnnotations() {
        String text = "This is a PDF file";
        String content = "This is an annotation";
        try {
            PDDocument pdf = testUtils.generatePDF(text);
            testUtils.addAnnotation(pdf, content);
            File pdfFile = testUtils.convertPDFtoFile(pdf);
            PairUtils pair = ps.parsePDF(pdfFile);
            String res = pair.getText();
            res = res.replaceAll("\r", "");
            res = res.replaceAll("\n", "");
            assertEquals(text, res);
            String annot = pair.getAnnotations();
            annot = annot.replaceAll("\r", "");
            annot = annot.replaceAll("\n", "");
            assertEquals("This is - " + content, annot);
            assertEquals(pair.removeFileExtension(pdfFile.getName()), pair.getFileName());
            pdfFile.deleteOnExit();
        } catch (IOException | PDFException e) {
            throw new RuntimeException("Test failed - Could not generate PDF");
        }
    }

    @Test
    public void testRemoveAbstract() {
        String text = "Abstract\r\ntext";
        assertEquals("text", ps.removeAbstract(text));
        assertEquals("text", ps.removeAbstract("text"));
    }

    @Test
    public void testNoHyphens() {
        String input = "This is a test string";
        String expected = "This is a test string";
        assertEquals(expected, ps.preprocess(input));
    }

    @Test
    public void testHyphenWindowsEOL() {
        String input = "This is a test -\r\nstring";
        String expected = "This is a test -\r\nstring";
        assertEquals(expected, ps.preprocess(input));
    }

    @Test
    public void testHyphenUnixEOL() {
        String input = "This is a test -\nstring";
        String expected = "This is a test -\nstring";
        assertEquals(expected, ps.preprocess(input));
    }

    @Test
    public void testHyphenMiddleOfText() {
        String input = "This is a test -string";
        String expected = "This is a test -string";
        assertEquals(expected, ps.preprocess(input));
    }

    @Test
    public void testHyphenAtEndOfText() {
        String input = "This is a test -";
        String expected = "This is a test -";
        assertEquals(expected, ps.preprocess(input));
    }

    @Test
    public void testMultipleHyphens() {
        String input = "This -is -a -test -string\r\nand -another\r\ntest";
        String expected = "This -is -a -test -string\r\nand -another\r\ntest";
        assertEquals(expected, ps.preprocess(input));
    }
    @Test
    public void testRemoveTextUnderTable() throws Exception {
        // Arrange
        ParsingService parsingService = new ParsingService(null);
        PDPage mockPage = Mockito.mock(PDPage.class);
        CustomPDFTextStripperByArea mockCustomStripper = Mockito.mock(CustomPDFTextStripperByArea.class);
        PDFTextStripperByArea mockStripper = Mockito.mock(PDFTextStripperByArea.class);
        Mockito.when(mockPage.getMediaBox()).thenReturn(new PDRectangle(0, 0, 500, 500));
        PDFObject table = new PDFObject(100, 100, 200, 200, 0);
        String text = "Figure 0: This is a caption.\nThis is some text.";
        float colOneStart = 50;
        float colOneEnd = 150;
        float colTwoStart = 250;
        float colTwoEnd = 350;
        boolean isTwoColumn = false;

        Mockito.when(mockCustomStripper.getYCoordinates()).thenReturn(new ArrayList<>(List.of(0f, 500f)));
        Mockito.when(mockStripper.getTextForRegion(any())).thenReturn(text);
        String result = parsingService.removeTextUnderObject(table, mockPage, colOneStart, colOneEnd,
            colTwoStart, colTwoEnd, text, isTwoColumn, mockCustomStripper, mockStripper);

        String expectedResult = "\nThis is some text.";
        assertEquals(expectedResult, result);
    }

    /*@Test
    public void testRemoveAbstract() {
        String text = "Abstract\nThis is a PDF file";
        try {
            File pdf = testUtils.convertPDFtoFile(testUtils.generatePDF(text));
            PairUtils pair = ps.parsePDF(pdf);
            String res = pair.getText();
            res = res.replaceAll("\r", "");
            res = res.replaceAll("\n", "");
            assertEquals("This is a PDF file", res);
            assertEquals("", pair.getAnnotations());
            assertEquals(pair.removeFileExtension(pdf.getName()), pair.getFileName());
            pdf.deleteOnExit();
        } catch (IOException | PDFException e) {
            throw new RuntimeException("Test failed - Could not generate PDF");
        }
    }*/
}
