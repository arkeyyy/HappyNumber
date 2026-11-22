module org.example.happy_number {
    requires javafx.controls;
    requires javafx.fxml;


    opens org.example.happy_number to javafx.fxml;
    exports org.example.happy_number;
}