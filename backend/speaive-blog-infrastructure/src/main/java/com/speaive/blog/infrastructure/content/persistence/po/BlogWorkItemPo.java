package com.speaive.blog.infrastructure.content.persistence.po;

public class BlogWorkItemPo {
    private String collectionId;
    private int itemOrder;
    private String contentType;
    private String contentSlug;

    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }
    public int getItemOrder() { return itemOrder; }
    public void setItemOrder(int itemOrder) { this.itemOrder = itemOrder; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getContentSlug() { return contentSlug; }
    public void setContentSlug(String contentSlug) { this.contentSlug = contentSlug; }
}
