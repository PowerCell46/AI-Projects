# If/else formatting

Leave exactly one blank line before each `} else` / `} else if`, separating one branch's body from
the next.

```java
// flag
if (decodedLowercasedFileName.endsWith(".xlsx")) {
    return FileContentType.XLSX;
} else if (decodedLowercasedFileName.endsWith(".xls")) {
    return FileContentType.XLS;
} else {
    return FileContentType.OTHER;
}

// prefer
if (decodedLowercasedFileName.endsWith(".xlsx")) {
    return FileContentType.XLSX;

} else if (decodedLowercasedFileName.endsWith(".xls")) {
    return FileContentType.XLS;

} else {
    return FileContentType.OTHER;
}
```
