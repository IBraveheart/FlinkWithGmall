package utils;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * @author Akang
 * @create 2023-07-05 11:23
 */
public class DateUtil {
    public static String getYMDHMS(Long dt){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        String format = sdf.format(new Date(dt));
        return format ;
    }

    public static String getYMD(Long dt){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String format = sdf.format(new Date(dt));
        return format ;
    }

    public static String converToDateTime(Long dt){
        LocalDateTime localDateTime = LocalDateTime.ofInstant(new Date(dt).toInstant(), ZoneId.of("+0"));
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME ;
        String datetime = dateTimeFormatter.format(localDateTime).replaceAll("T", " ");
        return datetime ;

    }


    public static void main(String[] args) {
        System.out.println(getYMDHMS(1688430801000L));
        System.out.println(getYMD(13300 * 24 * 60 * 60 * 1000L));
        System.out.println(converToDateTime(1689059879421L));
    }
}
