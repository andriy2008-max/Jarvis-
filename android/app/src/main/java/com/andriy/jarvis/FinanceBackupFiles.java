package com.andriy.jarvis;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.util.Arrays;

/** User-chosen password protects both portable backups and the pre-restore snapshot. */
public final class FinanceBackupFiles {
    private static final String PREVIOUS="finance-pre-restore.jfb";
    private static final int LIMIT=20*1024*1024;
    private FinanceBackupFiles(){}
    private static byte[] read(InputStream stream)throws IOException{
        if(stream==null)throw new FileNotFoundException("Не вдалося відкрити файл");
        try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];int n;
            while((n=stream.read(buf))!=-1){
                if(out.size()+n>LIMIT)throw new IOException("Файл перевищує 20 МБ");
                out.write(buf,0,n);
            }
            return out.toByteArray();
        }
    }
    public static void save(Context c,Uri file,char[] password)throws Exception{
        try(FinanceLedger ledger=new FinanceLedger(c)){
            byte[] bytes=FinanceBackup.export(ledger,password);
            try(OutputStream out=c.getContentResolver().openOutputStream(file)){
                if(out==null)throw new FileNotFoundException();
                out.write(bytes);
            }finally{Arrays.fill(bytes,(byte)0);}
        }
    }
    public static void restore(Context c,Uri file,char[] password)throws Exception{
        byte[] original;
        try(InputStream in=c.getContentResolver().openInputStream(file)){original=read(in);}
        try{
            // Invalid passwords/files never overwrite the last working rollback snapshot.
            FinanceBackup.validate(password,original);
            try(FinanceLedger ledger=new FinanceLedger(c)){
                android.database.sqlite.SQLiteDatabase db=ledger.getWritableDatabase();
                db.beginTransaction();
                try{
                    byte[] previous=FinanceBackup.export(ledger,password);
                    try{
                        File temporary=c.getFileStreamPath(PREVIOUS+".tmp");
                        File target=c.getFileStreamPath(PREVIOUS);
                        try(FileOutputStream out=c.openFileOutput(PREVIOUS+".tmp",Context.MODE_PRIVATE)){
                            out.write(previous);out.getFD().sync();
                        }
                        java.nio.file.Files.move(temporary.toPath(),target.toPath(),
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }finally{Arrays.fill(previous,(byte)0);}
                    FinanceBackup.restore(ledger,password,original);
                    db.setTransactionSuccessful();
                }finally{db.endTransaction();}
            }
        }finally{Arrays.fill(original,(byte)0);}
    }
    public static boolean hasPrevious(Context c){return c.getFileStreamPath(PREVIOUS).isFile();}
    public static void undoRestore(Context c,char[] password)throws Exception{
        byte[] bytes;
        try(InputStream in=c.openFileInput(PREVIOUS)){bytes=read(in);}
        try(FinanceLedger ledger=new FinanceLedger(c)){
            FinanceBackup.restore(ledger,password,bytes);
        }finally{Arrays.fill(bytes,(byte)0);}
    }
}
