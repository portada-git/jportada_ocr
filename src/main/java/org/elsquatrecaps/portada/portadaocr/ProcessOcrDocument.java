package org.elsquatrecaps.portada.portadaocr;


import com.google.api.client.util.Lists;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.documentai.v1beta3.Document;
import com.google.cloud.documentai.v1beta3.DocumentProcessorServiceClient;
import com.google.cloud.documentai.v1beta3.DocumentProcessorServiceSettings;
import com.google.cloud.documentai.v1beta3.ProcessRequest;
import com.google.cloud.documentai.v1beta3.ProcessResponse;
import com.google.cloud.documentai.v1beta3.RawDocument;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

public class ProcessOcrDocument {
    private static final String configFileName = "project_access.properties";
    private static final Map<String, String> mimeTypes = new HashMap<>();
    private String projectId;
    private String location; // Format is "us" or "eu".
    private String processorId;
    private String credentialsPath=null;
    private InputStream credentialsStream=null;
    private String filePath;
    private byte[] imageFileData;
    private Document documentResponse;
    static {
        mimeTypes.put("pdf", "application/pdf");
        mimeTypes.put("jpg", "image/jpeg");
        mimeTypes.put("jpeg", "image/jpeg");
        mimeTypes.put("png", "image/png");
        mimeTypes.put("tif", "image/tiff");
        mimeTypes.put("tiff", "image/tiff");
        mimeTypes.put("gif", "image/gif");
    }

    public ProcessOcrDocument() {
        
    }
    
    
    public void init(String basePath, String team, InputStream credentials) throws FileNotFoundException, IOException {
        StringBuilder strb = new StringBuilder(basePath);
        Properties prop = new Properties();
        if(!basePath.endsWith("/")){
            strb.append("/");
        }
        strb.append(team);
        if(!team.endsWith("/")){
            strb.append("/");
        }
        strb.append(configFileName);
        prop.load(new FileInputStream(strb.toString()));
        this.projectId = prop.getProperty("projectId");
        this.location = prop.getProperty("location");
        this.processorId = prop.getProperty("processorId");
        this.credentialsPath = null;
        this.credentialsStream = credentials;
    }
    
    public void init(String basePath, String team) throws FileNotFoundException, IOException {
        StringBuilder strb = new StringBuilder(basePath);
        Properties prop = new Properties();
        if(!basePath.endsWith("/")){
            strb.append("/");
        }
        strb.append(team);
        if(!team.endsWith("/")){
            strb.append("/");
        }
        strb.append(configFileName);
        prop.load(new FileInputStream(strb.toString()));
        this.projectId = prop.getProperty("projectId");
        this.location = prop.getProperty("location");
        this.processorId = prop.getProperty("processorId");
        this.credentialsPath = prop.getProperty("credentialsPath");
    }
    
    public String getCredentialsPath(){
        return credentialsPath;
    }
    
    public InputStream getCredentialsStream() throws FileNotFoundException{
        if(credentialsPath!=null){
            updateCredentialsStream();
        }
        return credentialsStream;
    }
    
    public void updateCredentialsStream() throws FileNotFoundException{
        this.credentialsStream = new FileInputStream(new File(credentialsPath).getAbsoluteFile());
    }
    
    public void setCredentialsStream(InputStream is){
        this.credentialsStream = is;
        this.credentialsPath=null;
    }

    public void setFilePath(String path) throws IOException{
        this.filePath = path;
        this.imageFileData = Files.readAllBytes(Paths.get(filePath));
    }
    
    public String getFilePath(){
        return filePath;
    }
    
    public void readImage(String imagePath) throws IOException{
        this.setFilePath(imagePath);
    }
    
    public void process(String imagePath) throws FileNotFoundException, IOException{
        readImage(imagePath);
        process();
    }
    
    public void process() throws FileNotFoundException, IOException{
        String extension = filePath.substring(filePath.lastIndexOf(".")+1);
        String mime = mimeTypes.get(extension);
        String endpoint = String.format("%s-documentai.googleapis.com:443", location);
        GoogleCredentials credentialsProvider = GoogleCredentials.fromStream(getCredentialsStream())
                .createScoped(Lists.newArrayList(Collections.singleton("https://www.googleapis.com/auth/cloud-platform")));
        DocumentProcessorServiceSettings settings =DocumentProcessorServiceSettings.newBuilder().setEndpoint(endpoint)
                .setCredentialsProvider(FixedCredentialsProvider.create(credentialsProvider)).build();
        try (DocumentProcessorServiceClient client = DocumentProcessorServiceClient.create(settings)) {
            String name = String.format("projects/%s/locations/%s/processors/%s", projectId, location, processorId);

            ByteString content = ByteString.copyFrom(imageFileData);

            RawDocument document = RawDocument.newBuilder().setContent(content)
                    .setMimeType(mime).build();

            ProcessRequest request = ProcessRequest.newBuilder().setName(name).setRawDocument(document).build();

            ProcessResponse result = client.processDocument(request);
            documentResponse = result.getDocument();
        }
    }
    
    public String getJsonString() throws InvalidProtocolBufferException{
        return JsonFormat.printer().print(documentResponse);
    }

    public Document getResult(){
        return documentResponse;
    }
    
    public void saveRawText(String path) throws IOException{
        try(FileWriter fw = new FileWriter(path)){
            fw.append(getText());
        }
    }
    
    public void saveText(String path) throws IOException{
        try(FileWriter fw = new FileWriter(path)){
            fw.append(getParagraphs(0));
        }
    }
    
    public String getText(){
        return documentResponse.getText();
    }
    
    public String getParagraphs(){
        return getParagraphs(-1);
    }
    
    public String getParagraphs(int s){
        StringBuilder strb = new StringBuilder();
        ParagraphPointer pointer = new ParagraphPointer(documentResponse);
        if(s<=0){
            s=documentResponse.getPages(0).getParagraphsCount();
            for(int i=1; i< documentResponse.getPagesCount(); i++){
                s+=documentResponse.getPages(i).getParagraphsCount();
            }
        }
        int i=0;
        while (i<s && pointer.hasNext()){
            strb.append(pointer.getNext());
            i++;
        }
        return strb.toString();
    }
    
    private static class ParagraphPointer extends Pointer{
        final static String EOL = "\n";
        boolean returnEol=false;
        int paragraf=0;

        public ParagraphPointer(Document doc) {
            super(doc);
        }
        
        public boolean hasNext(){
            if(paragraf>=document.getPages(page).getParagraphsCount()){
                if(returnEol){
                    paragraf=0;
                    page++;
                    returnEol=false;
                }else{
                    returnEol=true;
                }
            }
            return page<document.getPagesCount();
        }

        public String getNext(){
            String ret;
            String nextp;
            String cat="\n";
            if(returnEol){
                ret = EOL;
            }else{
                ret = getParagraph(page, paragraf, "\n");
                nextp = getParagraph(page, paragraf+1, "");
                int s = -1;
                Pattern p1 = Pattern.compile("^.*\\w\n$", Pattern.DOTALL);
                Pattern p2 = Pattern.compile("^\\s*[^A-ZÁÀÄÂÉÈËÊÍÌÏÎÓÒÖÔÚÙÜÛÑ].*$", Pattern.DOTALL);
                Pattern p3 = Pattern.compile("^.*[-¬]\n$", Pattern.DOTALL);
                if(p1.matcher(ret).matches() && p2.matcher(nextp).matches()){
                    cat = " ";
                }else if(p3.matcher(ret).matches()){
                    cat = "";
                    s=0;
                }
                ret = ret.replaceAll("[-¬]\\n", "").replaceAll(" ?\\n ?", " ");
                ret = ret.substring(0, ret.length()+s).concat(cat);
            }
            paragraf++;
            return ret;
        }
        
        private String getParagraph(int page, int paragraf, String def){
            String ret=def;
            if(paragraf>=document.getPages(page).getParagraphsCount()){
                if(page+1<document.getPagesCount()){
                    ret = getParagraph(page+1, 0, def);
                }
            }else{
                if (document.getPages(page).getParagraphs(paragraf).getLayout().getTextAnchor().getTextSegmentsList().size() > 0) {
                    int startIdx = (int) document.getPages(page).getParagraphs(paragraf).getLayout().getTextAnchor().getTextSegments(0).getStartIndex();
                    int endIdx = (int) document.getPages(page).getParagraphs(paragraf).getLayout().getTextAnchor().getTextSegments(0).getEndIndex();
                    ret = document.getText().substring(startIdx, endIdx);
                }
            }
            return ret;
        }
    }

    private static class Pointer{
        Document document;
        int page =0;

        public Pointer(Document doc) {
            document = doc;
        }
    }

//      System.out.println("Document processing complete.");
//
//      // Read the text recognition output from the processor
//      // For a full list of Document object attributes,
//      // please reference this page:
//      // https://googleapis.dev/java/google-cloud-document-ai/latest/index.html
//
//      // Get all of the document text as one big string
//      String text = documentResponse.getText();
//      System.out.printf("Full document text: '%s'\n", escapeNewlines(text));
//
//      // Read the text recognition output from the processor
//      List<Document.Page> pages = documentResponse.getPagesList();
//      System.out.printf("There are %s page(s) in this document.\n", pages.size());
//
//      for (Document.Page page : pages) {
//        System.out.printf("Page %d:\n", page.getPageNumber());
//        printPageDimensions(page.getDimension());
//        printDetectedLanguages(page.getDetectedLanguagesList());
//        printParagraphs(page.getParagraphsList(), text);
//        printBlocks(page.getBlocksList(), text);
//        printLines(page.getLinesList(), text);
//        printTokens(page.getTokensList(), text);
//      }
//    }
//  }

  private static void printPageDimensions(Document.Page.Dimension dimension) {
    String unit = dimension.getUnit();
    System.out.printf("    Width: %.1f %s\n", dimension.getWidth(), unit);
    System.out.printf("    Height: %.1f %s\n", dimension.getHeight(), unit);
  }

  private static void printDetectedLanguages(
      List<Document.Page.DetectedLanguage> detectedLangauges) {
    System.out.println("    Detected languages:");
    for (Document.Page.DetectedLanguage detectedLanguage : detectedLangauges) {
      String languageCode = detectedLanguage.getLanguageCode();
      float confidence = detectedLanguage.getConfidence();
      System.out.printf("        %s (%.2f%%)\n", languageCode, confidence * 100.0);
    }
  }

  private static void printParagraphs(List<Document.Page.Paragraph> paragraphs, String text) {
    System.out.printf("    %d paragraphs detected:\n", paragraphs.size());
    Document.Page.Paragraph firstParagraph = paragraphs.get(0);
    String firstParagraphText = getLayoutText(firstParagraph.getLayout().getTextAnchor(), text);
    System.out.printf("        First paragraph text: %s\n", escapeNewlines(firstParagraphText));
    Document.Page.Paragraph lastParagraph = paragraphs.get(paragraphs.size() - 1);
    String lastParagraphText = getLayoutText(lastParagraph.getLayout().getTextAnchor(), text);
    System.out.printf("        Last paragraph text: %s\n", escapeNewlines(lastParagraphText));
  }

  private static void printBlocks(List<Document.Page.Block> blocks, String text) {
    System.out.printf("    %d blocks detected:\n", blocks.size());
    Document.Page.Block firstBlock = blocks.get(0);
    String firstBlockText = getLayoutText(firstBlock.getLayout().getTextAnchor(), text);
    System.out.printf("        First block text: %s\n", escapeNewlines(firstBlockText));
    Document.Page.Block lastBlock = blocks.get(blocks.size() - 1);
    String lastBlockText = getLayoutText(lastBlock.getLayout().getTextAnchor(), text);
    System.out.printf("        Last block text: %s\n", escapeNewlines(lastBlockText));
  }

  private static void printLines(List<Document.Page.Line> lines, String text) {
    System.out.printf("    %d lines detected:\n", lines.size());
    Document.Page.Line firstLine = lines.get(0);
    String firstLineText = getLayoutText(firstLine.getLayout().getTextAnchor(), text);
    System.out.printf("        First line text: %s\n", escapeNewlines(firstLineText));
    Document.Page.Line lastLine = lines.get(lines.size() - 1);
    String lastLineText = getLayoutText(lastLine.getLayout().getTextAnchor(), text);
    System.out.printf("        Last line text: %s\n", escapeNewlines(lastLineText));
  }

  private static void printTokens(List<Document.Page.Token> tokens, String text) {
    System.out.printf("    %d tokens detected:\n", tokens.size());
    Document.Page.Token firstToken = tokens.get(0);
    String firstTokenText = getLayoutText(firstToken.getLayout().getTextAnchor(), text);
    System.out.printf("        First token text: %s\n", escapeNewlines(firstTokenText));
    Document.Page.Token lastToken = tokens.get(tokens.size() - 1);
    String lastTokenText = getLayoutText(lastToken.getLayout().getTextAnchor(), text);
    System.out.printf("        Last token text: %s\n", escapeNewlines(lastTokenText));
  }

  // Extract shards from the text field
  private static String getLayoutText(Document.TextAnchor textAnchor, String text) {
    if (textAnchor.getTextSegmentsList().size() > 0) {
      int startIdx = (int) textAnchor.getTextSegments(0).getStartIndex();
      int endIdx = (int) textAnchor.getTextSegments(0).getEndIndex();
      return text.substring(startIdx, endIdx);
    }
    return "[NO TEXT]";
  }

  private static String escapeNewlines(String s) {
    return s.replaceAll("\n", "\\n").replaceAll("\r", "\\r");
  }
}