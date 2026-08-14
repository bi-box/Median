package com.xinyv.median;

public final class BookmarkFolderStoreSelfTest {
    public static void main(String[] args) {
        BookmarkFolderStore store = new BookmarkFolderStore();
        BookmarkFolderStore.Folder work = store.create("工作", BookmarkFolderStore.ROOT, true);
        BookmarkFolderStore.Folder docs = store.create("文档", work.id, false);
        require(store.path(docs.id).equals("全部收藏 / 工作 / 文档"), "nested path failed");
        store.setParentForUrl("https://example.com/", docs.id);
        require(store.parentForUrl("https://example.com").equals(docs.id), "bookmark move failed");
        require(!store.update(work.id, "工作", docs.id, true), "folder cycle allowed");
        require(store.folder(work.id).showOnHome, "home folder flag lost");
        require(store.descendantFolderCount(work.id) == 1, "nested folder count failed");
        require(store.bookmarkCountInTree(work.id) == 1, "nested bookmark count failed");
        require(store.remove(work.id), "existing folder could not be removed");
        require(store.allFolders().isEmpty(), "recursive folder delete failed");
        require(store.parentForUrl("https://example.com").length() == 0, "orphan association retained");
        require(!store.remove(work.id), "missing folder reported as removed");
        System.out.println("BookmarkFolderStoreSelfTest passed");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
