package com.ShopOnline.Buy.online.exceptions;

import org.springframework.http.HttpStatus;

import java.util.Date;

public class ExceptionResponse {
    private Date timeStamp;
    private String message;
    private String details;
    private int status;

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public ExceptionResponse(Date timeStamp, String message, String details, int status) {
        this.timeStamp = timeStamp;
        this.message = message;
        this.details = details;
        this.status = status;
    }

    public Date getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(Date timeStamp) {
        this.timeStamp = timeStamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
