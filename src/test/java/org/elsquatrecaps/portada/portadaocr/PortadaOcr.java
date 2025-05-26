package org.elsquatrecaps.portada.portadaocr;



import com.google.cloud.documentai.v1beta3.Document;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author josepcanellas
 */
public class PortadaOcr {

    public static void main(String[] args) {
        boolean all=true;
        String[] files = {
            "inclinada_ordered_image_cleaned",  //0
            "curvada",                          //1
            "1852_08_05_BUE_LP_U_00_001",       //2
            "1852_08_03_BUE_LP_U_00_002",       //3
            "1852_08_02_BUE_LP_U_00_001",       //4
            "1852_07_22_BUE_LP_U_00_002",       //5
            "1852_07_20_BUE_LP_U_00_001",       //6
            "1850_01_15_BCN_DB_U_14_000_000",   //7
            "1850_01_13_BCN_DB_U_10_000_000",   //8
            "1850_01_09_BCN_DB_U_10_000_004",   //9
            "0013_1852_01_10_FIA_dl_10000031836_img10418410__Pagina13__DiarioDeBarcelonaAno18_002",
            "1860_06_08_BCN_DB_M_18_003",       //11
            "1860_06_08_BCN_DB_T_03_000"        //12
        };
        String path = files[11];
        
        if(all){
            processAll("./data/", "../ocr/", files);
        }else{
            processOne("./data/", "../ocr/", path);
        }
    }
    
    public static void processAll(String inDir, String outDir, String[] files){
        for(String file : files){
            processOne(inDir, outDir, file);
        }        
    }
    
    public static void processOne(String inDir, String outDir, String path){
        
        ProcessOcrDocument processOcrDocument = new ProcessOcrDocument();
        File ocrDir = new File(outDir);
        if(!ocrDir.exists()){
            ocrDir.mkdirs();
            //ocdDir.mkdir();
        }
        try {
            processOcrDocument.init(new File("./").getCanonicalFile().getAbsolutePath(), "bcn");
            processOcrDocument.process(inDir.concat(path).concat(".jpg"));
            Document dc = processOcrDocument.getResult();
            processOcrDocument.saveText(outDir.concat("result").concat(path).concat(".txt"));
            processOcrDocument.saveRawText(outDir.concat("result").concat(path).concat(".ori").concat(".txt"));
//            System.out.println(processOcrDocument.getJsonString());
            
//            processOcrDocument.process("../data/inclinada.jpg");
//            dc = processOcrDocument.getResult();
//            processOcrDocument.saveText("../ocr/inclinada.txt");
//            processOcrDocument.saveRawText("../ocr/inclinada.or.txt");
//            System.out.println(dc.getText());
            

        } catch (IOException ex) {
            Logger.getLogger(PortadaOcr.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
}
