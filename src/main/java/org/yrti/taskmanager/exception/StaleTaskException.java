package org.yrti.taskmanager.exception;
public class StaleTaskException extends RuntimeException { public StaleTaskException(Long id){super("Task " + id + " was modified by another request");} }
