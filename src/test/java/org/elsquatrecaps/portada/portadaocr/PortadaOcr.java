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
//        String path = "./data/inclinada_ordered_image_cleaned.jpg";
//        String path = "./data/curvada.jpg";
//        String path = "./data/1852_08_05_BUE_LP_U_00_001.jpg";
//        String path = "./data/1852_08_03_BUE_LP_U_00_002.jpg";
//        String path = "./data/1852_08_02_BUE_LP_U_00_001.jpg";
//        String path = "./data/1852_07_22_BUE_LP_U_00_002.jpg";
//        String path = "./data/1852_07_20_BUE_LP_U_00_001.jpg";
//        String path = "./data/1850_01_15_BCN_DB_U_14_000_000.jpg";
//        String path = "./data/1850_01_13_BCN_DB_U_10_000_000.jpg";
//        String path = "./data/1850_01_09_BCN_DB_U_10_000_004.jpg";
        String path = "./data/0013_1852_01_10_FIA_dl_10000031836_img10418410__Pagina13__DiarioDeBarcelonaAno18_002.jpg";
        
        ProcessOcrDocument processOcrDocument = new ProcessOcrDocument();
        File ocrDir = new File("../ocr");
        if(!ocrDir.exists()){
            ocrDir.mkdirs();
            //ocdDir.mkdir();
        }
        try {
            processOcrDocument.init(new File("./").getCanonicalFile().getAbsolutePath(), "bcn");
            processOcrDocument.process(path);
            Document dc = processOcrDocument.getResult();
            processOcrDocument.saveText("../ocr/result.txt");
            processOcrDocument.saveRawText("../ocr/result.ori.txt");
            System.out.println(processOcrDocument.getJsonString());
            
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
