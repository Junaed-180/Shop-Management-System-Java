import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;

public class ShopApp extends JFrame {

    private static final String URL = "jdbc:mysql://localhost:3306/shop_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASS = "";

    DefaultTableModel prodModel, cartModel;
    JTable prodTable, cartTable;
    JComboBox<String> billingSearch;
    JLabel totalLabel;

    public ShopApp() {
        setTitle("Shop Management System");
        setSize(1250, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        initDB();
        initUI();
        refreshProducts();
    }

    private void initDB() {
        try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", USER, PASS);
             Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE IF NOT EXISTS shop_db");
        } catch (Exception ignored) {}

        try (Connection c = DriverManager.getConnection(URL, USER, PASS);
             Statement s = c.createStatement()) {

            s.execute("CREATE TABLE IF NOT EXISTS products (" +
                    "product_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(100) NOT NULL," +
                    "quantity INT NOT NULL," +
                    "price DOUBLE NOT NULL)");

            s.execute("CREATE TABLE IF NOT EXISTS sales (" +
                    "sale_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "sale_date DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "total DOUBLE NOT NULL)");

            s.execute("CREATE TABLE IF NOT EXISTS sales_items (" +
                    "item_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "sale_id INT NOT NULL," +
                    "product_id INT NOT NULL," +
                    "quantity INT NOT NULL," +
                    "price DOUBLE NOT NULL," +
                    "total DOUBLE NOT NULL," +
                    "FOREIGN KEY (sale_id) REFERENCES sales(sale_id)," +
                    "FOREIGN KEY (product_id) REFERENCES products(product_id))");

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void initUI() {
        JPanel left = new JPanel(new BorderLayout(5,5));
        left.setPreferredSize(new Dimension(520,0));

        JPanel addPanel = new JPanel(new GridLayout(5,2,5,5));
        addPanel.setBorder(BorderFactory.createTitledBorder("Add / Update Product"));

        JTextField nameF = new JTextField();
        JTextField qtyF = new JTextField();
        JTextField priceF = new JTextField();
        JButton addBtn = new JButton("Add Product");
        JButton updateBtn = new JButton("Update Selected");
        JButton deleteBtn = new JButton("Delete Selected");

        addPanel.add(new JLabel("Name")); addPanel.add(nameF);
        addPanel.add(new JLabel("Qty")); addPanel.add(qtyF);
        addPanel.add(new JLabel("Price")); addPanel.add(priceF);
        addPanel.add(addBtn); addPanel.add(updateBtn);

        left.add(addPanel, BorderLayout.NORTH);

        prodModel = new DefaultTableModel(new String[]{"ID","Name","Qty","Price"},0) {
            public boolean isCellEditable(int row, int column){ return false; }
        };
        prodTable = new JTable(prodModel);
        prodTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        left.add(new JScrollPane(prodTable), BorderLayout.CENTER);

        JPanel deletePanel = new JPanel();
        deletePanel.add(deleteBtn);
        left.add(deletePanel, BorderLayout.SOUTH);

        add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new BorderLayout(5,5));
        right.setBorder(BorderFactory.createTitledBorder("Customer Billing"));

        JPanel billTop = new JPanel();
        billingSearch = new JComboBox<>();
        billingSearch.setPreferredSize(new Dimension(300,25));
        enableProductSearch(billingSearch);

        JTextField billQty = new JTextField(5);
        JButton addCart = new JButton("Add To Cart");

        billTop.add(new JLabel("Search Product"));
        billTop.add(billingSearch);
        billTop.add(new JLabel("Qty"));
        billTop.add(billQty);
        billTop.add(addCart);

        right.add(billTop, BorderLayout.NORTH);

        cartModel = new DefaultTableModel(new String[]{"ID","Name","Qty","Price","Total"},0) {
            public boolean isCellEditable(int row, int column){ return false; }
        };
        cartTable = new JTable(cartModel);
        right.add(new JScrollPane(cartTable), BorderLayout.CENTER);

        JPanel cartBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton removeItem = new JButton("Remove Selected");
        JButton clearCart = new JButton("Clear Cart");
        JButton finalizeBill = new JButton("Finalize Bill");
        totalLabel = new JLabel("Total: 0.00");
        totalLabel.setFont(new Font("Arial",Font.BOLD,16));

        cartBottom.add(removeItem);
        cartBottom.add(clearCart);
        cartBottom.add(finalizeBill);
        cartBottom.add(totalLabel);

        right.add(cartBottom, BorderLayout.SOUTH);

        add(right, BorderLayout.CENTER);

        Runnable updateTotal = () -> {
            double total = 0;
            for(int i = 0; i < cartModel.getRowCount(); i++) {
                total += (double)cartModel.getValueAt(i, 4);
            }
            totalLabel.setText(String.format("Total: %.2f", total));
        };

        // Add Product
        addBtn.addActionListener(e -> {
            String name = nameF.getText().trim();
            String qtyText = qtyF.getText().trim();
            String priceText = priceF.getText().trim();

            if(name.isEmpty() || qtyText.isEmpty() || priceText.isEmpty()) {
                JOptionPane.showMessageDialog(this,"Fill all fields");
                return;
            }

            try {
                int qty = Integer.parseInt(qtyText);
                double price = Double.parseDouble(priceText);

                if(qty <= 0 || price <= 0) {
                    JOptionPane.showMessageDialog(this,"Quantity and Price must be > 0");
                    return;
                }

                try(Connection c = DriverManager.getConnection(URL,USER,PASS);
                    PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO products(name,quantity,price) VALUES(?,?,?)")) {
                    ps.setString(1,name);
                    ps.setInt(2,qty);
                    ps.setDouble(3,price);
                    ps.executeUpdate();
                    JOptionPane.showMessageDialog(this,"Product added successfully!");
                    nameF.setText(""); qtyF.setText(""); priceF.setText("");
                    refreshProducts();
                }
            } catch(NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,"Enter valid number for Qty and Price");
            } catch(Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this,"Database error: " + ex.getMessage());
            }
        });

        // Update Product
        updateBtn.addActionListener(e -> {
            int selectedRow = prodTable.getSelectedRow();
            if(selectedRow==-1){ JOptionPane.showMessageDialog(this,"Select product to update"); return; }
            int pid=(int)prodModel.getValueAt(selectedRow,0);
            String name=nameF.getText().trim();
            String qtyText=qtyF.getText().trim();
            String priceText=priceF.getText().trim();
            if(name.isEmpty()||qtyText.isEmpty()||priceText.isEmpty()){ JOptionPane.showMessageDialog(this,"Fill all fields"); return; }
            try{
                int qty=Integer.parseInt(qtyText);
                double price=Double.parseDouble(priceText);
                if(qty <=0 || price<=0){ JOptionPane.showMessageDialog(this,"Quantity and Price must be >0"); return; }
                try(Connection c=DriverManager.getConnection(URL,USER,PASS);
                    PreparedStatement ps=c.prepareStatement("UPDATE products SET name=?, quantity=?, price=? WHERE product_id=?")){
                    ps.setString(1,name); ps.setInt(2,qty); ps.setDouble(3,price); ps.setInt(4,pid);
                    ps.executeUpdate(); nameF.setText(""); qtyF.setText(""); priceF.setText(""); refreshProducts();
                }
            } catch(NumberFormatException ex){ JOptionPane.showMessageDialog(this,"Enter valid number for Qty and Price"); }
            catch(Exception ex){ ex.printStackTrace(); JOptionPane.showMessageDialog(this,"Database error: "+ex.getMessage()); }
        });

        // Delete Product
        deleteBtn.addActionListener(e -> {
            int selectedRow = prodTable.getSelectedRow();
            if(selectedRow==-1){ JOptionPane.showMessageDialog(this,"Select product to delete"); return; }
            int pid=(int)prodModel.getValueAt(selectedRow,0);
            int confirm = JOptionPane.showConfirmDialog(this,"Delete this product?","Confirm",JOptionPane.YES_NO_OPTION);
            if(confirm!=JOptionPane.YES_OPTION) return;
            try(Connection c=DriverManager.getConnection(URL,USER,PASS);
                PreparedStatement ps=c.prepareStatement("DELETE FROM products WHERE product_id=?")){
                ps.setInt(1,pid); ps.executeUpdate(); refreshProducts();
            } catch(Exception ex){ ex.printStackTrace(); JOptionPane.showMessageDialog(this,"Database error: "+ex.getMessage()); }
        });

        // Cart Actions
        addCart.addActionListener(e -> {
            try{
                String text=(String)billingSearch.getSelectedItem();
                if(text==null){ JOptionPane.showMessageDialog(this,"Select a product"); return; }
                String qtyInput=billQty.getText().trim();
                if(qtyInput.isEmpty()){ JOptionPane.showMessageDialog(this,"Enter quantity"); return; }
                int q=Integer.parseInt(qtyInput);
                String[] parts=text.split(" - ");
                int id=Integer.parseInt(parts[0]);
                String name=parts[1];
                int availableQty=Integer.parseInt(parts[2]);
                double price=Double.parseDouble(parts[3]);
                if(q<=0){ JOptionPane.showMessageDialog(this,"Quantity must be >0"); return; }
                if(q>availableQty){ JOptionPane.showMessageDialog(this,"Quantity exceeds stock"); return; }
                cartModel.addRow(new Object[]{id,name,q,price,q*price});
                billQty.setText(""); updateTotal.run();
            } catch(NumberFormatException ex){ JOptionPane.showMessageDialog(this,"Enter valid number"); }
        });

        removeItem.addActionListener(e -> { int r=cartTable.getSelectedRow(); if(r!=-1){ cartModel.removeRow(r); updateTotal.run(); } });
        clearCart.addActionListener(e -> { if(cartModel.getRowCount()>0){ int c=JOptionPane.showConfirmDialog(this,"Clear cart?","Confirm",JOptionPane.YES_NO_OPTION); if(c==JOptionPane.YES_OPTION){ cartModel.setRowCount(0); updateTotal.run(); } } });

        // Finalize Bill
        finalizeBill.addActionListener(e -> {
            if(cartModel.getRowCount()==0){ JOptionPane.showMessageDialog(this,"Cart empty"); return; }
            try(Connection c=DriverManager.getConnection(URL,USER,PASS)){
                c.setAutoCommit(false);

                // Calculate total
                double totalAmount = 0;
                for(int i=0;i<cartModel.getRowCount();i++){
                    totalAmount += (double)cartModel.getValueAt(i,4);
                }

                // Insert into sales table
                PreparedStatement psSale = c.prepareStatement(
                        "INSERT INTO sales(total) VALUES(?)",
                        Statement.RETURN_GENERATED_KEYS
                );
                psSale.setDouble(1,totalAmount);
                psSale.executeUpdate();
                ResultSet rs = psSale.getGeneratedKeys();
                rs.next();
                int saleId = rs.getInt(1);

                // Insert into sales_items table
                PreparedStatement psItem = c.prepareStatement(
                        "INSERT INTO sales_items(sale_id, product_id, quantity, price, total) VALUES(?,?,?,?,?)"
                );
                PreparedStatement psUpdate = c.prepareStatement("UPDATE products SET quantity=quantity-? WHERE product_id=?");

                for(int i=0;i<cartModel.getRowCount();i++){
                    int pid=(int)cartModel.getValueAt(i,0);
                    int qty=(int)cartModel.getValueAt(i,2);
                    double price=(double)cartModel.getValueAt(i,3);
                    double total=(double)cartModel.getValueAt(i,4);

                    psItem.setInt(1,saleId);
                    psItem.setInt(2,pid);
                    psItem.setInt(3,qty);
                    psItem.setDouble(4,price);
                    psItem.setDouble(5,total);
                    psItem.addBatch();

                    psUpdate.setInt(1,qty);
                    psUpdate.setInt(2,pid);
                    psUpdate.addBatch();
                }

                psItem.executeBatch();
                psUpdate.executeBatch();
                c.commit();

                cartModel.setRowCount(0);
                refreshProducts();
                updateTotal.run();
                JOptionPane.showMessageDialog(this,"Bill finalized successfully!");
                System.out.println("FINISH");
            } catch(Exception ex){ ex.printStackTrace(); JOptionPane.showMessageDialog(this,"Error finalizing bill"); }
        });
    }

    private void enableProductSearch(JComboBox<String> combo){
        combo.setEditable(true);
        JTextField tf=(JTextField)combo.getEditor().getEditorComponent();
        tf.addKeyListener(new KeyAdapter(){
            public void keyReleased(KeyEvent e){
                String text=tf.getText();
                combo.removeAllItems();
                try(Connection c=DriverManager.getConnection(URL,USER,PASS);
                    PreparedStatement ps=c.prepareStatement("SELECT * FROM products WHERE name LIKE ?")) {
                    ps.setString(1,"%"+text+"%");
                    ResultSet rs=ps.executeQuery();
                    while(rs.next()){
                        combo.addItem(rs.getInt("product_id")+" - "+rs.getString("name")+" - "+rs.getInt("quantity")+" - "+rs.getDouble("price"));
                    }
                }catch(Exception ex){ ex.printStackTrace(); }
                tf.setText(text); combo.showPopup();
            }
        });
    }

    private void refreshProducts(){
        prodModel.setRowCount(0);
        try(Connection c=DriverManager.getConnection(URL,USER,PASS);
            Statement s=c.createStatement();
            ResultSet rs=s.executeQuery("SELECT * FROM products")){
            while(rs.next()){
                prodModel.addRow(new Object[]{rs.getInt("product_id"),rs.getString("name"),rs.getInt("quantity"),rs.getDouble("price")});
            }
        } catch(Exception ex){ ex.printStackTrace(); }
    }

    public static void main(String[] args){
        try{ Class.forName("com.mysql.cj.jdbc.Driver"); } catch(Exception ignored){}
        SwingUtilities.invokeLater(() -> new ShopApp().setVisible(true));
    }
}
