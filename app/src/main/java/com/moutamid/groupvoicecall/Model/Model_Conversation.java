package com.moutamid.groupvoicecall.Model;

public class Model_Conversation {
    private boolean mic_status;
    private String id;

    public Model_Conversation() {
    }

    public Model_Conversation(boolean mic_status, String id) {
        this.mic_status = mic_status;
        this.id = id;
    }

    public boolean isMic_status() {
        return mic_status;
    }

    public void setMic_status(boolean mic_status) {
        this.mic_status = mic_status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
