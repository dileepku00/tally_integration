package com.tally;

import java.io.IOException;

/**
 * Tally Ledger Dashboard Backend
 *
 */
public class App 
{
    public static void main( String[] args ) throws IOException
    {
        Server server = new Server();
        server.start();
    }
}