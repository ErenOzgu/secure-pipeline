package demo;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Wrong on purpose. See pom.xml. */
public class Bad {

    private static final String PASSWORD = "hunter2";

    public String fingerprint(String text) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        return new String(md.digest(text.getBytes()));
    }

    public ResultSet find(Connection connection, String name) throws SQLException {
        Statement statement = connection.createStatement();
        return statement.executeQuery("select * from users where name = '" + name + "' and secret = '" + PASSWORD + "'");
    }

    public int firstByte(String path) throws IOException {
        FileInputStream in = new FileInputStream(path);
        return in.read();
    }

    public boolean same(int a) {
        return a == a;
    }
}
