package com.checklistboteco.backend.purchases.csv;

import java.text.Normalizer;
import java.util.*;

public final class CsvSupport {
    private CsvSupport() {}

    public static char detectDelimiter(String csv){
        String line=csv==null?"":csv.lines().findFirst().orElse("");
        char best=','; int max=-1;
        for(char candidate:new char[]{',',';','\t','|'}){
            int count=0; boolean quoted=false;
            for(int i=0;i<line.length();i++){
                char c=line.charAt(i);
                if(c=='\"') quoted=!quoted;
                else if(c==candidate&&!quoted) count++;
            }
            if(count>max){ max=count; best=candidate; }
        }
        return best;
    }

    public static List<List<String>> parse(String csv,char delimiter){
        List<List<String>> rows=new ArrayList<>(); List<String> row=new ArrayList<>(); StringBuilder cell=new StringBuilder(); boolean quoted=false;
        String source=csv==null?"":csv.replace("\r\n","\n").replace('\r','\n');
        for(int i=0;i<source.length();i++){
            char c=source.charAt(i);
            if(c=='\"'){
                if(quoted&&i+1<source.length()&&source.charAt(i+1)=='\"'){ cell.append('\"'); i++; }
                else quoted=!quoted;
            } else if(c==delimiter&&!quoted){ row.add(cell.toString().trim()); cell.setLength(0); }
            else if(c=='\n'&&!quoted){ row.add(cell.toString().trim()); cell.setLength(0); if(row.stream().anyMatch(v->!v.isBlank())) rows.add(row); row=new ArrayList<>(); }
            else cell.append(c);
        }
        row.add(cell.toString().trim()); if(row.stream().anyMatch(v->!v.isBlank())) rows.add(row);
        return rows;
    }

    public static String key(String value){
        String normalized=Normalizer.normalize(Objects.toString(value,""),Normalizer.Form.NFD).replaceAll("\\p{M}","")
            .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","_").replaceAll("^_+|_+$","");
        return normalized.isBlank()?"column":normalized;
    }
}
