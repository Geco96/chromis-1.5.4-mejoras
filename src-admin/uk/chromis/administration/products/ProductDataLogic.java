package uk.chromis.administration.products;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import javafx.collections.ObservableList;
import javafx.scene.control.TableView;
import org.tinylog.Logger;
import uk.chromis.administration.auxiliary.AuxiliaryTableInfo;
import uk.chromis.administration.dialogs.Dialogs;
import uk.chromis.administration.recipes.IngredientsTableInfo;
import uk.chromis.administration.recipes.RecipeLineInfo;
import uk.chromis.administration.suppliers.SuppliedProductInfo;
import uk.chromis.administration.tax.TaxDataLogic;
import uk.chromis.basic.BasicException;
import uk.chromis.data.loader.DataRead;
import uk.chromis.data.loader.Datas;
import uk.chromis.data.loader.PreparedSentence;
import uk.chromis.data.loader.SentenceExec;
import uk.chromis.data.loader.SentenceExecTransaction;
import uk.chromis.data.loader.SerializerRead;
import uk.chromis.data.loader.SerializerReadDouble;
import uk.chromis.data.loader.SerializerReadInteger;
import uk.chromis.data.loader.SerializerReadString;
import uk.chromis.data.loader.SerializerWriteBasic;
import uk.chromis.data.loader.SerializerWriteBasicExt;
import uk.chromis.data.loader.SerializerWriteString;
import uk.chromis.data.loader.Session;
import uk.chromis.data.loader.SessionFactory;
import uk.chromis.data.loader.StaticSentence;
import uk.chromis.data.loader.Transaction;
import uk.chromis.data.model.Field;
import uk.chromis.data.model.Row;
import uk.chromis.format.Formats;

public class ProductDataLogic {
   protected Session session = SessionFactory.getInstance().getSession();
   protected Row productsRow;
   protected Row CategoryRow;
   protected Datas[] stockdiaryDatas;
   protected Datas[] stockAdjustDatas = new Datas[]{Datas.STRING, Datas.STRING, Datas.DOUBLE, Datas.STRING};
   protected StaticSentence productFieldChecks;
   protected StaticSentence categoriesFieldChecks;
   private final SentenceExec updatePricing;
   private final SentenceExec updatePricingInc;

   public ProductDataLogic() {
      this.stockdiaryDatas = new Datas[]{
         Datas.STRING,
         Datas.TIMESTAMP,
         Datas.INT,
         Datas.STRING,
         Datas.STRING,
         Datas.STRING,
         Datas.DOUBLE,
         Datas.DOUBLE,
         Datas.STRING,
         Datas.STRING,
         Datas.STRING,
         Datas.STRING,
         Datas.STRING,
         Datas.STRING,
         Datas.DOUBLE,
         Datas.DOUBLE,
         Datas.DOUBLE,
         Datas.DOUBLE,
         Datas.DOUBLE,
         Datas.STRING
      };
      this.productsRow = new Row(
         new Field("id", Datas.STRING, Formats.STRING),
         new Field("reference", Datas.STRING, Formats.STRING),
         new Field("code", Datas.STRING, Formats.STRING),
         new Field("codetype", Datas.STRING, Formats.STRING),
         new Field("name", Datas.STRING, Formats.STRING),
         new Field("pricebuy", Datas.DOUBLE, Formats.DOUBLE),
         new Field("pricesell", Datas.DOUBLE, Formats.DOUBLE),
         new Field("pricesellinc", Datas.DOUBLE, Formats.DOUBLE),
         new Field("category", Datas.STRING, Formats.STRING),
         new Field("taxcat", Datas.STRING, Formats.STRING),
         new Field("attributeset_id", Datas.STRING, Formats.STRING),
         new Field("iscom", Datas.BOOLEAN, Formats.BOOLEAN),
         new Field("isscale", Datas.BOOLEAN, Formats.BOOLEAN),
         new Field("iskitchen", Datas.BOOLEAN, Formats.BOOLEAN),
         new Field("kitchendescription", Datas.STRING, Formats.STRING),
         new Field("isservice", Datas.BOOLEAN, Formats.BOOLEAN),
         new Field("display", Datas.STRING, Formats.STRING),
         new Field("attributes", Datas.BYTES, Formats.NULL),
         new Field("isvprice", Datas.BOOLEAN, Formats.BOOLEAN),
         new Field("isverpatrib", Datas.BOOLEAN, Formats.BOOLEAN),
         new Field("warranty", Datas.BOOLEAN, Formats.BOOLEAN),
         new Field("image", Datas.IMAGE, Formats.NULL),
         new Field("alias", Datas.STRING, Formats.STRING),
         new Field("alwaysavailable", Datas.BOOLEAN, Formats.BOOLEAN),
         new Field("candiscount", Datas.DOUBLE, Formats.DOUBLE),
         new Field("iscatalog", Datas.BOOLEAN, Formats.BOOLEAN),
         new Field("catorder", Datas.INT, Formats.INT),
         new Field("managestock", Datas.BOOLEAN, Formats.BOOLEAN),
         new Field("commission", Datas.DOUBLE, Formats.DOUBLE),
         new Field("remotedisplay", Datas.BOOLEAN, Formats.BOOLEAN),
         new Field("averagecost", Datas.DOUBLE, Formats.DOUBLE),
         new Field("burnvalue", Datas.INT, Formats.INT),
         new Field("earnvalue", Datas.INT, Formats.INT),
         new Field("loyaltymultiplier", Datas.DOUBLE, Formats.DOUBLE),
         new Field("sellunit", Datas.STRING, Formats.STRING),
         new Field("stockunit", Datas.STRING, Formats.STRING),
         new Field("buyunit", Datas.STRING, Formats.STRING),
         new Field("agerestricted", Datas.INT, Formats.INT),
         new Field("isrecipe", Datas.BOOLEAN, Formats.BOOLEAN),
         new Field("isingredient", Datas.BOOLEAN, Formats.BOOLEAN),
         new Field("retired", Datas.BOOLEAN, Formats.BOOLEAN),
         new Field("auxiliarypermitted", Datas.BOOLEAN, Formats.BOOLEAN),
         new Field("systemobject", Datas.BOOLEAN, Formats.BOOLEAN),
         new Field("remoteprinter", Datas.STRING, Formats.STRING),
         new Field("remotedisplayid", Datas.INT, Formats.INT),
         new Field("buytax", Datas.STRING, Formats.STRING),
         new Field("siteguid", Datas.STRING, Formats.STRING)
      );
      this.productFieldChecks = new StaticSentence(
         this.session,
         "select  'reference', CAST(count(*) as CHAR(5))  from products  where reference = ?  and id <> ?  union all  select  'code', CAST(count(*) as CHAR(5))  from products  where code = ?  and id <> ?  union all  select  'name', CAST(count(*) as CHAR(5))  from products  where name = ?  and id <> ? ",
         new SerializerWriteBasic(Datas.STRING, Datas.STRING, Datas.STRING, Datas.STRING, Datas.STRING, Datas.STRING),
         uk.chromis.administration.KeyedData.getSerializerRead()
      );
      this.updatePricing = new StaticSentence(
         this.session, "update products set pricesell = ? where id = ?  ", new SerializerWriteBasic(Datas.DOUBLE, Datas.STRING)
      );
      this.updatePricingInc = new StaticSentence(
         this.session, "update products set pricesellinc = ? where id = ? ", new SerializerWriteBasic(Datas.DOUBLE, Datas.STRING)
      );
   }

   public ObservableList<ProductTableInfo> getProductsForTableView(String siteGuid) {
      try {
         return new PreparedSentence(
               this.session,
               "select  p.id, reference, code, p.name, alias, c.name, pricesell, pricebuy, pricesellinc, isingredient, iscom,  0 from products as p join categories as c on p.category=c.id where retired = false  and p.isrecipe = false  and p.category != 'loyaltycards'  and p.siteguid = ? order by reference ",
               SerializerWriteString.INSTANCE,
               ProductTableInfo.getSerializerRead()
            )
            .observablelist(siteGuid);
      } catch (BasicException var3) {
         Dialogs.errorAlert(var3, "Get Product list Error");
         return null;
      }
   }

   public ObservableList<ProductTableInfo> getProductsForAuxiliaryTableViews(String siteGuid) {
      System.out.println("getProductsForAuxiliaryTableView");

      try {
         return new PreparedSentence(
               this.session,
               "select  p.id, reference, code, p.name, alias, c.name, pricesell, pricebuy, pricesellinc, isingredient, iscom,  (select count(*) from products_com where product = p.id) as used from products as p join categories as c on p.category=c.id where retired = false  and p.systemobject = false and p.auxiliarypermitted = true and p.siteguid = ? order by reference ",
               SerializerWriteString.INSTANCE,
               ProductTableInfo.getSerializerRead()
            )
            .observablelist(siteGuid);
      } catch (BasicException var3) {
         Dialogs.errorAlert(var3, "Get Product list Error");
         return null;
      }
   }

   public ObservableList<ProductTableInfo> getProductsFreeSearch(String searchStr) {
      try {
         return new PreparedSentence(
               this.session,
               "select  p.id, reference, code, p.name, alias, c.name, pricesell, pricebuy, pricesellinc, isingredient, iscom,  0 from products as p join categories as c on p.category=c.id where "
                  + searchStr,
               null,
               ProductTableInfo.getSerializerRead()
            )
            .observablelist();
      } catch (BasicException var3) {
         Dialogs.errorAlert(var3, "Get Product list Error");
         return null;
      }
   }

   public ObservableList<ProductTableInfo> getProductsByValueSearch(String searchStr, String siteguid) {
      try {
         return new PreparedSentence(
               this.session,
               "select  p.id, reference, code, p.name, alias, c.name, pricesell, pricebuy, pricesellinc, isingredient, iscom, 0 from products as p join categories as c on p.category=c.id where "
                  + searchStr
                  + " and retired = false  and p.siteguid = ? order by reference ",
               SerializerWriteString.INSTANCE,
               ProductTableInfo.getSerializerRead()
            )
            .observablelist(siteguid);
      } catch (BasicException var4) {
         Dialogs.errorAlert(var4, "Get Product list Error");
         return null;
      }
   }

   public ObservableList<ProductTableInfo> getProductRecipeList(String siteguid) {
      try {
         return new PreparedSentence(
               this.session,
               "select  p.id, reference, code, p.name, alias, c.name, pricesell, pricebuy, pricesellinc, isingredient, iscom,  0 from products as p join categories as c on p.category=c.id where retired = false  and p.siteguid = ?  and isrecipe = true  order by reference ",
               SerializerWriteString.INSTANCE,
               ProductTableInfo.getSerializerRead()
            )
            .observablelist(siteguid);
      } catch (BasicException var3) {
         Dialogs.errorAlert(var3, "Get Product list Error");
         return null;
      }
   }

   public ObservableList<AuxiliaryTableInfo> getAuxilaryProductList(String product, String siteGuid) {
      try {
         return new PreparedSentence(
               this.session,
               "select  distinct p.id,  p.reference,  p.name,  p.pricesell,  p.pricesellinc,  ?,  p.siteguid,  case when  (select id FROM products_com where product = ? and product2 = p.id and c.siteguid = ? ) is null then false  else  true  end as assigned  from products as p  left join products_com as c on p.id=c.product2  where iscom = true and p.siteguid = ? and retired = false order by p.name ",
               new SerializerWriteBasicExt(new Datas[]{Datas.STRING, Datas.STRING, Datas.STRING, Datas.STRING}, new int[]{0, 1, 2, 3}),
               AuxiliaryTableInfo.getSerializerRead()
            )
            .observablelist(new Object[]{product, product, siteGuid, siteGuid});
      } catch (BasicException var4) {
         Dialogs.errorAlert(var4, "Get Product list Error");
         return null;
      }
   }

   public ObservableList<AuxiliaryTableInfo> getAllAuxilaryProductList(String siteGuid) {
      try {
         return new PreparedSentence(
               this.session,
               "select  distinct p.id,  p.reference,  p.name,  p.pricesell,  p.pricesellinc,  '',  p.siteguid,  false  from products as p  where iscom = true and p.siteguid = ? and retired = false order by p.name ",
               SerializerWriteString.INSTANCE,
               AuxiliaryTableInfo.getSerializerRead()
            )
            .observablelist(siteGuid);
      } catch (BasicException var3) {
         Dialogs.errorAlert(var3, "Get Product list Error");
         return null;
      }
   }

   public ObservableList<SuppliedProductInfo> getSuppliedProductsByValueSearch(String searchStr, String siteguid) {
      try {
         return new PreparedSentence(
               this.session,
               "select  p.id, reference, code, p.name, alias, c.name, pricesell, pricebuy, pricesellinc, isingredient, iscom from products as p join categories as c on p.category=c.id where "
                  + searchStr
                  + " and retired = false  and p.siteguid = ? order by reference ",
               SerializerWriteString.INSTANCE,
               SuppliedProductInfo.getSerializerRead()
            )
            .observablelist(searchStr);
      } catch (BasicException var4) {
         Dialogs.errorAlert(var4, "Get Product list Error");
         return null;
      }
   }

   public ObservableList<StockLevelInfo> getProductLevelsList(String product, String siteguid) {
      try {
         return new PreparedSentence(
               this.session,
               "select  l.id as level_id,  lc.id as location_id,  lc.name as location_name,   s.product,  s.units,  l.stocksecurity,  l.stockmaximum,  s.siteguid  from stockcurrent as s  inner join stocklevel as l on (s.product=l.product and s.location = l.location)  inner join products as p on s.product =p.id  JOIN locations AS lc ON lc.id=l.location  where s.product = ?  and l.siteguid = ? ",
               new SerializerWriteBasicExt(new Datas[]{Datas.STRING, Datas.STRING}, new int[]{0, 1}),
               StockLevelInfo.getSerializerRead()
            )
            .observablelist(new Object[]{product, siteguid});
      } catch (BasicException var4) {
         Dialogs.errorAlert(var4, "Get Product list Error");
         return null;
      }
   }

   public List<BarcodeInfo> getProductBarcodes(String product) {
      try {
         return new PreparedSentence(
               this.session,
               "select  b.id, b.productid, b.barcode, b.sellingqty, u.id as uomid, u.uomfactor, u.uomdescription from barcodes as b join uom as u on u.id = b.uomid where b.productid = ? and b.id != b.productid ",
               SerializerWriteString.INSTANCE,
               BarcodeInfo.getSerializerRead()
            )
            .list(product);
      } catch (BasicException var3) {
         Dialogs.errorAlert(var3, "Get get barcodes error error");
         return null;
      }
   }

   public List<RecipeLineInfo> getrecipeLines(String product, String siteguid) {
      try {
         return new PreparedSentence(
               this.session,
               "select  id, product, ingredient,  quantity,  siteguid  from recipe_ingredients  where product = ? and siteguid = ? ",
               new SerializerWriteBasicExt(new Datas[]{Datas.STRING, Datas.STRING}, new int[]{0, 1}),
               RecipeLineInfo.getSerializerRead()
            )
            .list(new Object[]{product, siteguid});
      } catch (BasicException var4) {
         Dialogs.errorAlert(var4, "Get get recipe line error");
         return null;
      }
   }

   public List<ProductSupplierInfo> getProductSuppliers(String product) {
      try {
         return new PreparedSentence(
               this.session,
               "select  p.id, p.productid, p.supplierid, s.suppliername, p.stockcode, u.id as uomid, u.uomfactor, u.uomdescription,  p.packcost,  p.taxrate  from productsuppliers  as p join suppliers as s on p.supplierid=s.id join uom as u on u.id=p.uomid where productid = ? ",
               SerializerWriteString.INSTANCE,
               ProductSupplierInfo.getSerializerRead()
            )
            .list(product);
      } catch (BasicException var3) {
         Dialogs.errorAlert(var3, "Get search Product error");
         return null;
      }
   }

   public List<uk.chromis.administration.KeyedData> getAllIngredients(String id) {
      try {
         return new PreparedSentence(
               this.session,
               "select  id, concat(name,  ' - ',  reference) from products where isingredient = true  and siteguid =?  and retired = false order by name ",
               SerializerWriteString.INSTANCE,
               uk.chromis.administration.KeyedData.getSerializerRead()
            )
            .list(id);
      } catch (BasicException var3) {
         Dialogs.errorAlert(var3, "Get Product list error");
         return null;
      }
   }

   public List<ProductInfo> getProductsOnly(String siteGuid) {
      try {
         return new PreparedSentence(
               this.session,
               "select " + ProductData.getFieldList() + " from products  where siteguid = ?  and isrecipe = false  and retired = false order by reference ",
               SerializerWriteString.INSTANCE,
               ProductInfo.getSerializerRead()
            )
            .list(siteGuid);
      } catch (BasicException var3) {
         Dialogs.errorAlert(var3, "Get Product list Error");
         return null;
      }
   }

   public List<ProductInfo> getProductsByTax(String tax) {
      try {
         return new PreparedSentence(
               this.session,
               "select " + ProductData.getFieldList() + " from products  where taxcat = ? ",
               SerializerWriteString.INSTANCE,
               ProductInfo.getSerializerRead()
            )
            .list(tax);
      } catch (BasicException var3) {
         Dialogs.errorAlert(var3, "Get Product list Error");
         return null;
      }
   }

   public final void updatePricing(String product, Double price) {
      try {
         this.updatePricing.exec(price, product);
      } catch (BasicException var4) {
         Dialogs.errorAlert(var4, "Updatepring save error");
      }
   }

   public final void updatePricingInc(String product, Double price) {
      try {
         this.updatePricingInc.exec(price, product);
      } catch (BasicException var4) {
         Dialogs.errorAlert(var4, "Updatepring save error");
      }
   }

   public List<ProductInfo> getNonSystemProductsOnly(String siteGuid) {
      try {
         return new PreparedSentence(
               this.session,
               "select "
                  + ProductData.getFieldList()
                  + " from products  where siteguid = ?  and (isrecipe = false  and systemobject = false)  and retired = false  order by reference ",
               SerializerWriteString.INSTANCE,
               ProductInfo.getSerializerRead()
            )
            .list(siteGuid);
      } catch (BasicException var3) {
         Dialogs.errorAlert(var3, "Get Product list Error");
         return null;
      }
   }

   public ProductInfo getProduct(String id) {
      try {
         return (ProductInfo)new StaticSentence(
               this.session,
               "select " + ProductData.getFieldList() + " from products where id = ?  and retired = false",
               SerializerWriteString.INSTANCE,
               ProductInfo.getSerializerRead()
            )
            .find(id);
      } catch (BasicException var3) {
         Dialogs.errorAlert(var3, "Get Product error");
         return null;
      }
   }

   public List<String> getBarcodesForProduct(String productid) {
      try {
         return new PreparedSentence(
               this.session, "select  id  from barcodes where productid = ? ", SerializerWriteString.INSTANCE, SerializerReadString.INSTANCE
            )
            .list(productid);
      } catch (BasicException var3) {
         Dialogs.errorAlert(var3, "Get list error");
         return null;
      }
   }

   public List<String> getSuppliersForProduct(String productid) {
      try {
         return new PreparedSentence(
               this.session, "select  id  from productsuppliers where productid = ? ", SerializerWriteString.INSTANCE, SerializerReadString.INSTANCE
            )
            .list(productid);
      } catch (BasicException var3) {
         Dialogs.errorAlert(var3, "Get list error");
         return null;
      }
   }

   public List<String> getLevelsForProduct(String productid) {
      try {
         return new PreparedSentence(
               this.session, "select  id  from stocklevel where product = ? ", SerializerWriteString.INSTANCE, SerializerReadString.INSTANCE
            )
            .list(productid);
      } catch (BasicException var3) {
         Dialogs.errorAlert(var3, "Get list error");
         return null;
      }
   }

   public void productImportInsert(final Object params) throws BasicException {
      Transaction t = new Transaction(this.session) {
         @Override
         public Object transact() throws BasicException {
            Object[] values = (Object[])params;
            int i = new PreparedSentence(
                  ProductDataLogic.this.session,
                  "insert into products (" + ProductDataLogic.this.getProductFieldList("") + ") values (" + ProductDataLogic.this.getProductFieldValues() + ")",
                  new SerializerWriteBasicExt(ProductDataLogic.this.productsRow.getDatas(), ProductDataLogic.this.getProductIndexInsert())
               )
               .exec(values);
            i = new PreparedSentence(
                  ProductDataLogic.this.session,
                  "insert into barcodes (id, productid, barcode, sellingqty, uomid, uomfactor, description)  values (?, ?, ?, ?, ?, ?, ?) ",
                  new SerializerWriteBasicExt(
                     new Datas[]{Datas.STRING, Datas.STRING, Datas.STRING, Datas.DOUBLE, Datas.STRING, Datas.DOUBLE, Datas.STRING},
                     new int[]{0, 1, 2, 3, 4, 5, 6}
                  )
               )
               .exec(new Object[]{UUID.randomUUID().toString(), values[0], values[2], 1.0, "000-each", 1.0, values[4]});
            return null;
         }
      };
      t.execute();
   }

   public final SentenceExec createStockRecord() {
      return new SentenceExecTransaction(this.session) {
         @Override
         public int execInTransaction(Object params) throws BasicException {
            Object[] values = (Object[])params;
            return new PreparedSentence(
                  ProductDataLogic.this.session,
                  "insert into stockcurrent ( location,  product,  units,  siteguid ) values (?, ?, 0.0, ?)",
                  new SerializerWriteBasic(Datas.STRING, Datas.STRING, Datas.STRING)
               )
               .exec(new Object[]{values[0], values[1], values[2]});
         }
      };
   }

   public final SentenceExec productImportUpdate() {
      return new SentenceExecTransaction(this.session) {
         @Override
         public int execInTransaction(Object params) throws BasicException {
            Object[] values = (Object[])params;
            return new PreparedSentence(
                  ProductDataLogic.this.session,
                  "update products set " + ProductDataLogic.this.getProductFieldUpdate("") + " where id = ? and siteguid = ? ",
                  new SerializerWriteBasicExt(ProductDataLogic.this.productsRow.getDatas(), ProductDataLogic.this.getProductIndexUpdate())
               )
               .exec(params);
         }
      };
   }

   public final SentenceExec productInsert() {
      return new SentenceExecTransaction(this.session) {
         @Override
         public int execInTransaction(Object params) throws BasicException {
            Object[] values = (Object[])params;
            int i = new PreparedSentence(
                  ProductDataLogic.this.session,
                  "insert into products (" + ProductDataLogic.this.getProductFieldList("") + ") values (" + ProductDataLogic.this.getProductFieldValues() + ")",
                  new SerializerWriteBasicExt(ProductDataLogic.this.productsRow.getDatas(), ProductDataLogic.this.getProductIndexInsert())
               )
               .exec(params);
            new PreparedSentence(
                  ProductDataLogic.this.session,
                  "insert into stockcurrent (location, product, units, siteguid) values ('MainStore', ?, 0.0, ?)",
                  new SerializerWriteBasicExt(ProductDataLogic.this.productsRow.getDatas(), new int[]{ProductData.INDEX_ID, ProductData.INDEX_SITEGUID})
               )
               .exec(params);
            TableView barcodes = (TableView)values[ProductData.INDEX_TABLEVIEWBARCODES];
            TableView suppliers = (TableView)values[ProductData.INDEX_TABLEVIEWSUPPLIERS];
            TableView stockLevels = (TableView)values[ProductData.INDEX_STOCKLEVELS];
            List<TierPricing> prices = (List<TierPricing>)values[ProductData.INDEX_PRICING];

            try {
               new PreparedSentence(
                     ProductDataLogic.this.session,
                     "insert into tierpricing (productid,  tier1_qty, tier1_sellpriceinc, tier1_sellprice, tier2_qty, tier2_sellpriceinc, tier2_sellprice, tier3_qty, tier3_sellpriceinc, tier3_sellprice, tier4_qty, tier4_sellpriceinc, tier4_sellprice, tier5_qty, tier5_sellpriceinc, tier5_sellprice,  siteguid) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ",
                     new SerializerWriteBasic(
                        Datas.STRING,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.STRING
                     )
                  )
                  .exec(
                     new Object[]{
                        values[ProductData.INDEX_ID],
                        prices.get(0).getTierQty(),
                        prices.get(0).getTierPriceInc(),
                        prices.get(0).getTierPrice(),
                        prices.get(1).getTierQty(),
                        prices.get(1).getTierPriceInc(),
                        prices.get(1).getTierPrice(),
                        prices.get(2).getTierQty(),
                        prices.get(2).getTierPriceInc(),
                        prices.get(2).getTierPrice(),
                        prices.get(3).getTierQty(),
                        prices.get(3).getTierPriceInc(),
                        prices.get(3).getTierPrice(),
                        prices.get(4).getTierQty(),
                        prices.get(4).getTierPriceInc(),
                        prices.get(4).getTierPrice(),
                        values[ProductData.INDEX_SITEGUID]
                     }
                  );
            } catch (BasicException var10) {
               Logger.error((Throwable)var10);
            }

            try {
               new PreparedSentence(
                     ProductDataLogic.this.session,
                     "insert into barcodes (id, productid, barcode, sellingqty, uomid, uomfactor, description, siteguid) values (?, ?, ?, ?, ?, ?, ?, ?)  ",
                     new SerializerWriteBasic(Datas.STRING, Datas.STRING, Datas.STRING, Datas.DOUBLE, Datas.STRING, Datas.DOUBLE, Datas.STRING, Datas.STRING)
                  )
                  .exec(
                     new Object[]{
                        (String)values[0],
                        (String)values[0],
                        values[ProductData.INDEX_CODE].toString(),
                        1.0,
                        "000-each",
                        1.0,
                        values[ProductData.INDEX_NAME].toString(),
                        values[ProductData.INDEX_SITEGUID]
                     }
                  );
            } catch (BasicException var9) {
               Logger.error((Throwable)var9);
            }

            barcodes.getItems()
               .forEach(
                  b -> {
                     try {
                        BarcodeInfo barcode = (BarcodeInfo)b;
                        new PreparedSentence(
                              ProductDataLogic.this.session,
                              "insert into barcodes (id, productid, barcode, sellingqty, uomid, uomfactor, description, siteguid) values (?, ?, ?, ?, ?, ?, ?, ?)  ",
                              new SerializerWriteBasic(
                                 Datas.STRING, Datas.STRING, Datas.STRING, Datas.DOUBLE, Datas.STRING, Datas.DOUBLE, Datas.STRING, Datas.STRING
                              )
                           )
                           .exec(
                              new Object[]{
                                 barcode.getId(),
                                 (String)values[0],
                                 barcode.getBarcode(),
                                 barcode.getSellQty(),
                                 barcode.getUomid(),
                                 barcode.getUomfactor(),
                                 null,
                                 values[ProductData.INDEX_SITEGUID]
                              }
                           );
                     } catch (BasicException var4x) {
                        Logger.error((Throwable)var4x);
                     }
                  }
               );
            stockLevels.getItems()
               .forEach(
                  l -> {
                     try {
                        StockLevelInfo level = (StockLevelInfo)l;
                        new PreparedSentence(
                              ProductDataLogic.this.session,
                              "insert into stocklevel (id, location, product, stocksecurity, stockmaximum, siteguid) values (?, ?, ?, ?, ?, ?)  ",
                              new SerializerWriteBasic(Datas.STRING, Datas.STRING, Datas.STRING, Datas.DOUBLE, Datas.DOUBLE, Datas.STRING)
                           )
                           .exec(
                              new Object[]{
                                 level.getId(), level.getLocationId(), (String)values[0], level.getMinimum(), level.getMaximum(), level.getSiteguid()
                              }
                           );
                     } catch (BasicException var4x) {
                        Logger.error((Throwable)var4x);
                     }
                  }
               );
            TaxDataLogic taxDataLogic = new TaxDataLogic();
            suppliers.getItems()
               .forEach(
                  b -> {
                     try {
                        ProductSupplierInfo supplier = (ProductSupplierInfo)b;
                        new PreparedSentence(
                              ProductDataLogic.this.session,
                              "insert into productsuppliers (id, productid, supplierid, stockcode, uomid, packcost, taxrate) values (?, ?, ?, ?, ?, ?, ?)  ",
                              new SerializerWriteBasic(Datas.STRING, Datas.STRING, Datas.STRING, Datas.STRING, Datas.STRING, Datas.DOUBLE, Datas.DOUBLE)
                           )
                           .exec(
                              new Object[]{
                                 supplier.getId(),
                                 (String)values[0],
                                 supplier.getSupplierId(),
                                 supplier.getSupplierCode(),
                                 supplier.getUomId(),
                                 supplier.getPackCost(),
                                 taxDataLogic.getTaxRate((String)values[ProductData.INDEX_BUYTAX])
                              }
                           );
                     } catch (BasicException var5x) {
                        Logger.error((Throwable)var5x);
                     }
                  }
               );
            return i;
         }
      };
   }

   public final SentenceExec productUpdate() {
      return new SentenceExecTransaction(this.session) {
         @Override
         public int execInTransaction(Object params) throws BasicException {
            Object[] values = (Object[])params;
            int i = new PreparedSentence(
                  ProductDataLogic.this.session,
                  "update products set " + ProductDataLogic.this.getProductFieldUpdate("") + " where id = ? and siteguid = ? ",
                  new SerializerWriteBasicExt(ProductDataLogic.this.productsRow.getDatas(), ProductDataLogic.this.getProductIndexUpdate())
               )
               .exec(params);
            TableView barcodes = (TableView)values[ProductData.INDEX_TABLEVIEWBARCODES];
            TableView suppliers = (TableView)values[ProductData.INDEX_TABLEVIEWSUPPLIERS];
            TableView stockLevels = (TableView)values[ProductData.INDEX_STOCKLEVELS];
            List<String> barcodeList = ProductDataLogic.this.getBarcodesForProduct((String)values[ProductData.INDEX_ID]);
            List<String> supplierList = ProductDataLogic.this.getSuppliersForProduct((String)values[ProductData.INDEX_ID]);
            List<String> levelsList = ProductDataLogic.this.getLevelsForProduct((String)values[ProductData.INDEX_ID]);
            List<TierPricing> prices = (List<TierPricing>)values[ProductData.INDEX_PRICING];

            try {
               new PreparedSentence(
                     ProductDataLogic.this.session,
                     "replace into tierpricing (productid,  tier1_qty, tier1_sellpriceinc, tier1_sellprice, tier2_qty, tier2_sellpriceinc, tier2_sellprice, tier3_qty, tier3_sellpriceinc, tier3_sellprice, tier4_qty, tier4_sellpriceinc, tier4_sellprice, tier5_qty, tier5_sellpriceinc, tier5_sellprice, siteguid) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ",
                     new SerializerWriteBasic(
                        Datas.STRING,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.STRING
                     )
                  )
                  .exec(
                     new Object[]{
                        values[ProductData.INDEX_ID],
                        prices.get(0).getTierQty(),
                        prices.get(0).getTierPriceInc(),
                        prices.get(0).getTierPrice(),
                        prices.get(1).getTierQty(),
                        prices.get(1).getTierPriceInc(),
                        prices.get(1).getTierPrice(),
                        prices.get(2).getTierQty(),
                        prices.get(2).getTierPriceInc(),
                        prices.get(2).getTierPrice(),
                        prices.get(3).getTierQty(),
                        prices.get(3).getTierPriceInc(),
                        prices.get(3).getTierPrice(),
                        prices.get(4).getTierQty(),
                        prices.get(4).getTierPriceInc(),
                        prices.get(4).getTierPrice(),
                        values[ProductData.INDEX_SITEGUID]
                     }
                  );
            } catch (BasicException var15) {
               Logger.error((Throwable)var15);
            }

            try {
               new PreparedSentence(
                     ProductDataLogic.this.session,
                     "replace into barcodes (id, productid, barcode, sellingqty, uomid, uomfactor, description) values (?, ?, ?, ?, ?, ?, ?)  ",
                     new SerializerWriteBasic(Datas.STRING, Datas.STRING, Datas.STRING, Datas.DOUBLE, Datas.STRING, Datas.DOUBLE, Datas.STRING)
                  )
                  .exec(
                     new Object[]{
                        (String)values[0],
                        (String)values[0],
                        values[ProductData.INDEX_CODE].toString(),
                        1.0,
                        "000-each",
                        1.0,
                        values[ProductData.INDEX_NAME].toString()
                     }
                  );
            } catch (BasicException var14) {
               Logger.error((Throwable)var14);
            }

            barcodes.getItems()
               .forEach(
                  bxx -> {
                     try {
                        BarcodeInfo barcode = (BarcodeInfo)bxx;
                        barcodeList.remove(barcode.getId());
                        new PreparedSentence(
                              ProductDataLogic.this.session,
                              "replace into barcodes (id, productid, barcode, sellingqty, uomid, uomfactor, description) values (?, ?, ?, ?, ?, ?, ?)  ",
                              new SerializerWriteBasic(Datas.STRING, Datas.STRING, Datas.STRING, Datas.DOUBLE, Datas.STRING, Datas.DOUBLE, Datas.STRING)
                           )
                           .exec(
                              new Object[]{
                                 barcode.getId(),
                                 (String)values[0],
                                 barcode.getBarcode(),
                                 barcode.getSellQty(),
                                 barcode.getUomid(),
                                 barcode.getUomfactor(),
                                 null
                              }
                           );
                     } catch (BasicException var5x) {
                        Logger.error((Throwable)var5x);
                     }
                  }
               );

            for (String b : barcodeList) {
               new StaticSentence(ProductDataLogic.this.session, "delete from barcodes where id = ? and (id != productid) ", SerializerWriteString.INSTANCE)
                  .exec(b);
            }

            if (!(Boolean)values[ProductData.INDEX_MANAGESTOCK]) {
               new StaticSentence(
                     ProductDataLogic.this.session,
                     "update stockcurrent set units = 0.00  where product = ? and siteguid = ? ",
                     new SerializerWriteBasic(Datas.STRING, Datas.STRING)
                  )
                  .exec(new Object[]{(String)values[0], (String)values[ProductData.INDEX_SITEGUID]});
            }

            new StaticSentence(
                  ProductDataLogic.this.session,
                  "delete from stocklevel where product = ? and siteguid = ? ",
                  new SerializerWriteBasic(Datas.STRING, Datas.STRING)
               )
               .exec(new Object[]{(String)values[0], (String)values[ProductData.INDEX_SITEGUID]});
            if ((Boolean)values[ProductData.INDEX_MANAGESTOCK]) {
               stockLevels.getItems()
                  .forEach(
                     l -> {
                        try {
                           StockLevelInfo level = (StockLevelInfo)l;
                           // Only create the row when missing: the units shown were read when the product was
                           // opened, and writing them back would undo the sales made since then
                           new PreparedSentence(
                                 ProductDataLogic.this.session,
                                 "insert ignore into stockcurrent (location, product, units, siteguid) values (?, ?, ?, ?)  ",
                                 new SerializerWriteBasic(Datas.STRING, Datas.STRING, Datas.DOUBLE, Datas.STRING)
                              )
                              .exec(new Object[]{level.getLocationId(), (String)values[0], level.getCurrent(), level.getSiteguid()});
                           new PreparedSentence(
                                 ProductDataLogic.this.session,
                                 "replace into stocklevel (id, location, product, stocksecurity, stockmaximum, siteguid) values (?, ?, ?, ?, ?, ?)  ",
                                 new SerializerWriteBasic(Datas.STRING, Datas.STRING, Datas.STRING, Datas.DOUBLE, Datas.DOUBLE, Datas.STRING)
                              )
                              .exec(
                                 new Object[]{
                                    level.getId(), level.getLocationId(), (String)values[0], level.getMinimum(), level.getMaximum(), level.getSiteguid()
                                 }
                              );
                        } catch (BasicException var4x) {
                           Logger.error((Throwable)var4x);
                        }
                     }
                  );
            }

            TaxDataLogic taxDataLogic = new TaxDataLogic();
            suppliers.getItems()
               .forEach(
                  bxx -> {
                     try {
                        ProductSupplierInfo supplier = (ProductSupplierInfo)bxx;
                        supplierList.remove(supplier.getId());
                        new PreparedSentence(
                              ProductDataLogic.this.session,
                              "replace into productsuppliers (id, productid, supplierid, stockcode, uomid, packcost, taxrate) values (?, ?, ?, ?, ?, ?, ?)  ",
                              new SerializerWriteBasic(Datas.STRING, Datas.STRING, Datas.STRING, Datas.STRING, Datas.STRING, Datas.DOUBLE, Datas.DOUBLE)
                           )
                           .exec(
                              new Object[]{
                                 supplier.getId(),
                                 (String)values[0],
                                 supplier.getSupplierId(),
                                 supplier.getSupplierCode(),
                                 supplier.getUomId(),
                                 supplier.getPackCost(),
                                 taxDataLogic.getTaxRate((String)values[ProductData.INDEX_BUYTAX])
                              }
                           );
                     } catch (BasicException var6x) {
                        Logger.error((Throwable)var6x);
                     }
                  }
               );

            for (String b : supplierList) {
               new StaticSentence(ProductDataLogic.this.session, "delete from productsuppliers where id = ?", SerializerWriteString.INSTANCE).exec(b);
            }

            return i;
         }
      };
   }

   public final SentenceExec productTaxUpdate() {
      return new SentenceExecTransaction(this.session) {
         @Override
         public int execInTransaction(Object params) throws BasicException {
            Object[] values = (Object[])params;
            return new PreparedSentence(
                  ProductDataLogic.this.session,
                  "Insert into sql_tasks ( id, sql_execution, effectivedate) values (?, ?, ?)",
                  new SerializerWriteBasic(Datas.STRING, Datas.STRING, Datas.TIMESTAMP)
               )
               .exec(params);
         }
      };
   }

   public final SentenceExec productPricingUpdate() {
      return new SentenceExecTransaction(this.session) {
         @Override
         public int execInTransaction(Object params) throws BasicException {
            Object[] values = (Object[])params;
            int i = new PreparedSentence(
                  ProductDataLogic.this.session,
                  "Insert into product_pricingchange ( id,  pricebuy, pricesell, pricesellinc, taxcat, effectivedate, siteguid) values (?, ?, ?, ?, ?, ?, ?)",
                  new SerializerWriteBasic(Datas.STRING, Datas.DOUBLE, Datas.DOUBLE, Datas.DOUBLE, Datas.STRING, Datas.TIMESTAMP, Datas.STRING)
               )
               .exec(params);
            List<TierPricing> prices = (List<TierPricing>)values[7];

            try {
               new PreparedSentence(
                     ProductDataLogic.this.session,
                     "insert into tierpricingchange (productid,  tier1_qty, tier1_sellpriceinc, tier1_sellprice, tier2_qty, tier2_sellpriceinc, tier2_sellprice, tier3_qty, tier3_sellpriceinc, tier3_sellprice, tier4_qty, tier4_sellpriceinc, tier4_sellprice, tier5_qty, tier5_sellpriceinc, tier5_sellprice,  siteguid)  values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ",
                     new SerializerWriteBasic(
                        Datas.STRING,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.STRING
                     )
                  )
                  .exec(
                     new Object[]{
                        values[0],
                        prices.get(0).getTierQty(),
                        prices.get(0).getTierPriceInc(),
                        prices.get(0).getTierPrice(),
                        prices.get(1).getTierQty(),
                        prices.get(1).getTierPriceInc(),
                        prices.get(1).getTierPrice(),
                        prices.get(2).getTierQty(),
                        prices.get(2).getTierPriceInc(),
                        prices.get(2).getTierPrice(),
                        prices.get(3).getTierQty(),
                        prices.get(3).getTierPriceInc(),
                        prices.get(3).getTierPrice(),
                        prices.get(4).getTierQty(),
                        prices.get(4).getTierPriceInc(),
                        prices.get(4).getTierPrice(),
                        values[6]
                     }
                  );
            } catch (BasicException var6) {
               Logger.error((Throwable)var6);
            }

            return i;
         }
      };
   }

   public void runPricingUpdate() throws BasicException {
      new PreparedSentence(
            this.session,
            " update products as p  inner join product_pricingchange as pc on p.id = pc.id  set p.pricesellinc = pc.pricesellinc,  p.pricebuy = pc.pricebuy,  p.pricesell = pc.pricesell,  p.taxcat = pc.taxcat,  pc.processed = true  where p.siteguid = pc.siteguid  and pc.effectivedate <= now() and pc.processed = false; update tierpricing as t  inner join tierpricingchange as tc on t.productid = tc.productid  set  t.tier1_qty = t.tier1_qty,  t.tier1_sellprice = t.tier1_sellprice,  t.tier1_sellpriceinc = t.tier1_sellpriceinc,  t.tier2_qty = t.tier2_qty,  t.tier2_sellprice = t.tier2_sellprice,  t.tier2_sellpriceinc = t.tier2_sellpriceinc,  t.tier3_qty = t.tier3_qty,  t.tier3_sellprice = t.tier3_sellprice,  t.tier3_sellpriceinc = t.tier3_sellpriceinc,  t.tier4_qty = t.tier4_qty,  t.tier4_sellprice = t.tier4_sellprice,  t.tier4_sellpriceinc = t.tier4_sellpriceinc,  t.tier5_qty = t.tier4_qty,  t.tier5_sellprice = t.tier5_sellprice,  t.tier5_sellpriceinc = t.tier5_sellpriceinc,  tc.processed = true  where t.siteguid = tc.siteguid  and tc.effectivedate <= now()  and tc.processed = false;  delete from product_pricingchange where processed = true;  delete from tierpricingchange where processed = true "
         )
         .exec();
   }

   public final SentenceExec recipeInsert() {
      return new SentenceExecTransaction(this.session) {
         @Override
         public int execInTransaction(Object params) throws BasicException {
            Object[] values = (Object[])params;
            int i = new PreparedSentence(
                  ProductDataLogic.this.session,
                  "insert into products (" + ProductDataLogic.this.getProductFieldList("") + ") values (" + ProductDataLogic.this.getProductFieldValues() + ")",
                  new SerializerWriteBasicExt(ProductDataLogic.this.productsRow.getDatas(), ProductDataLogic.this.getProductIndexInsert())
               )
               .exec(params);
            new PreparedSentence(
                  ProductDataLogic.this.session,
                  "insert into stockcurrent (location, product, units, siteguid) values ('MainStore', ?, 0.0, ?)",
                  new SerializerWriteBasicExt(ProductDataLogic.this.productsRow.getDatas(), new int[]{ProductData.INDEX_ID, ProductData.INDEX_SITEGUID})
               )
               .exec(params);
            TableView ingredients = (TableView)values[ProductData.INDEX_INGREDIENTS];
            List<TierPricing> prices = (List<TierPricing>)values[ProductData.INDEX_PRICING];

            try {
               new PreparedSentence(
                     ProductDataLogic.this.session,
                     "insert into tierpricing (productid,  tier1_qty, tier1_sellpriceinc, tier1_sellprice, tier2_qty, tier2_sellpriceinc, tier2_sellprice, tier3_qty, tier3_sellpriceinc, tier3_sellprice, tier4_qty, tier4_sellpriceinc, tier4_sellprice, tier5_qty, tier5_sellpriceinc, tier5_sellprice, siteguid) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ",
                     new SerializerWriteBasic(
                        Datas.STRING,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.STRING
                     )
                  )
                  .exec(
                     new Object[]{
                        values[ProductData.INDEX_ID],
                        prices.get(0).getTierQty(),
                        prices.get(0).getTierPriceInc(),
                        prices.get(0).getTierPrice(),
                        prices.get(1).getTierQty(),
                        prices.get(1).getTierPriceInc(),
                        prices.get(1).getTierPrice(),
                        prices.get(2).getTierQty(),
                        prices.get(2).getTierPriceInc(),
                        prices.get(2).getTierPrice(),
                        prices.get(3).getTierQty(),
                        prices.get(3).getTierPriceInc(),
                        prices.get(3).getTierPrice(),
                        prices.get(4).getTierQty(),
                        prices.get(4).getTierPriceInc(),
                        prices.get(4).getTierPrice(),
                        values[ProductData.INDEX_SITEGUID]
                     }
                  );
            } catch (BasicException var9) {
               Logger.error((Throwable)var9);
            }

            try {
               new PreparedSentence(
                     ProductDataLogic.this.session,
                     "insert into barcodes (id, productid, barcode, sellingqty, uomid, uomfactor, description) values (?, ?, ?, ?, ?, ?, ?)  ",
                     new SerializerWriteBasic(Datas.STRING, Datas.STRING, Datas.STRING, Datas.DOUBLE, Datas.STRING, Datas.DOUBLE, Datas.STRING)
                  )
                  .exec(
                     new Object[]{
                        (String)values[0],
                        (String)values[0],
                        values[ProductData.INDEX_CODE].toString(),
                        1.0,
                        "000-each",
                        1.0,
                        values[ProductData.INDEX_NAME].toString()
                     }
                  );
            } catch (BasicException var8) {
               Logger.error((Throwable)var8);
            }

            new TaxDataLogic();
            Double cost = 0.0;
            new StaticSentence(
                  ProductDataLogic.this.session,
                  "update products set pricebuy = 0.00 where id = ? and siteguid = ? ",
                  new SerializerWriteBasic(Datas.STRING, Datas.STRING)
               )
               .exec(new Object[]{(String)values[0], (String)values[ProductData.INDEX_SITEGUID]});
            ingredients.getItems()
               .forEach(
                  b -> {
                     try {
                        IngredientsTableInfo ingredient = (IngredientsTableInfo)b;
                        new PreparedSentence(
                              ProductDataLogic.this.session,
                              "replace into recipe_ingredients (id, product, ingredient, quantity, siteguid) values (?, ?, ?, ?, ?)  ",
                              new SerializerWriteBasic(Datas.STRING, Datas.STRING, Datas.STRING, Datas.DOUBLE, Datas.STRING)
                           )
                           .exec(new Object[]{ingredient.getId(), (String)values[0], ingredient.getIngredient(), ingredient.getQty(), ingredient.getSiteguid()});
                        new PreparedSentence(
                              ProductDataLogic.this.session,
                              "update products set pricebuy = pricebuy + ?, averagecost = pricebuy  where id = ? and siteguid = ? ",
                              new SerializerWriteBasic(Datas.DOUBLE, Datas.STRING, Datas.STRING)
                           )
                           .exec(new Object[]{ingredient.getQty() * ingredient.getBuyPrice(), (String)values[0], (String)values[ProductData.INDEX_SITEGUID]});
                     } catch (BasicException var4x) {
                        Logger.error((Throwable)var4x);
                     }
                  }
               );
            return i;
         }
      };
   }

   public final SentenceExec recipeUpdate() {
      return new SentenceExecTransaction(this.session) {
         @Override
         public int execInTransaction(Object params) throws BasicException {
            Object[] values = (Object[])params;
            int i = new PreparedSentence(
                  ProductDataLogic.this.session,
                  "update products set " + ProductDataLogic.this.getProductFieldUpdate("") + " where id = ? and siteguid = ? ",
                  new SerializerWriteBasicExt(ProductDataLogic.this.productsRow.getDatas(), ProductDataLogic.this.getProductIndexUpdate())
               )
               .exec(params);
            TableView ingredients = (TableView)values[ProductData.INDEX_INGREDIENTS];
            List<TierPricing> prices = (List<TierPricing>)values[ProductData.INDEX_PRICING];

            try {
               new PreparedSentence(
                     ProductDataLogic.this.session,
                     "replace into tierpricing (productid,  tier1_qty, tier1_sellpriceinc, tier1_sellprice, tier2_qty, tier2_sellpriceinc, tier2_sellprice, tier3_qty, tier3_sellpriceinc, tier3_sellprice, tier4_qty, tier4_sellpriceinc, tier4_sellprice, tier5_qty, tier5_sellpriceinc, tier5_sellprice, siteguid) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ",
                     new SerializerWriteBasic(
                        Datas.STRING,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.INT,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.STRING
                     )
                  )
                  .exec(
                     new Object[]{
                        values[ProductData.INDEX_ID],
                        prices.get(0).getTierQty(),
                        prices.get(0).getTierPriceInc(),
                        prices.get(0).getTierPrice(),
                        prices.get(1).getTierQty(),
                        prices.get(1).getTierPriceInc(),
                        prices.get(1).getTierPrice(),
                        prices.get(2).getTierQty(),
                        prices.get(2).getTierPriceInc(),
                        prices.get(2).getTierPrice(),
                        prices.get(3).getTierQty(),
                        prices.get(3).getTierPriceInc(),
                        prices.get(3).getTierPrice(),
                        prices.get(4).getTierQty(),
                        prices.get(4).getTierPriceInc(),
                        prices.get(4).getTierPrice(),
                        values[ProductData.INDEX_SITEGUID]
                     }
                  );
            } catch (BasicException var9) {
               Logger.error((Throwable)var9);
            }

            try {
               new PreparedSentence(
                     ProductDataLogic.this.session,
                     "replace into barcodes (id, productid, barcode, sellingqty, uomid, uomfactor, description) values (?, ?, ?, ?, ?, ?, ?)  ",
                     new SerializerWriteBasic(Datas.STRING, Datas.STRING, Datas.STRING, Datas.DOUBLE, Datas.STRING, Datas.DOUBLE, Datas.STRING)
                  )
                  .exec(
                     new Object[]{
                        (String)values[0],
                        (String)values[0],
                        values[ProductData.INDEX_CODE].toString(),
                        1.0,
                        "000-each",
                        1.0,
                        values[ProductData.INDEX_NAME].toString()
                     }
                  );
            } catch (BasicException var8) {
               Logger.error((Throwable)var8);
            }

            new StaticSentence(
                  ProductDataLogic.this.session,
                  "delete from recipe_ingredients where product = ? and siteguid = ? ",
                  new SerializerWriteBasic(Datas.STRING, Datas.STRING)
               )
               .exec(new Object[]{(String)values[0], (String)values[ProductData.INDEX_SITEGUID]});
            Double cost = 0.0;
            new StaticSentence(
                  ProductDataLogic.this.session,
                  "update products set pricebuy = 0.00 where id = ? and siteguid = ? ",
                  new SerializerWriteBasic(Datas.STRING, Datas.STRING)
               )
               .exec(new Object[]{(String)values[0], (String)values[ProductData.INDEX_SITEGUID]});
            ingredients.getItems()
               .forEach(
                  b -> {
                     try {
                        IngredientsTableInfo ingredient = (IngredientsTableInfo)b;
                        new PreparedSentence(
                              ProductDataLogic.this.session,
                              "replace into recipe_ingredients (id, product, ingredient, quantity, siteguid) values (?, ?, ?, ?, ?)  ",
                              new SerializerWriteBasic(Datas.STRING, Datas.STRING, Datas.STRING, Datas.DOUBLE, Datas.STRING)
                           )
                           .exec(
                              new Object[]{
                                 ingredient.getId(), ingredient.getProduct(), ingredient.getIngredient(), ingredient.getQty(), ingredient.getSiteguid()
                              }
                           );
                        new PreparedSentence(
                              ProductDataLogic.this.session,
                              "update products set pricebuy = pricebuy + ?, averagecost = pricebuy  where id = ? and siteguid = ? ",
                              new SerializerWriteBasic(Datas.DOUBLE, Datas.STRING, Datas.STRING)
                           )
                           .exec(new Object[]{ingredient.getQty() * ingredient.getBuyPrice(), (String)values[0], (String)values[ProductData.INDEX_SITEGUID]});
                     } catch (BasicException var4x) {
                        Logger.error((Throwable)var4x);
                     }
                  }
               );
            new TaxDataLogic();
            return i;
         }
      };
   }

   public final SentenceExec processAuxiliary() {
      return new SentenceExecTransaction(this.session) {
         @Override
         public int execInTransaction(Object params) throws BasicException {
            Object[] values = (Object[])params;
            TableView auxiliaries = (TableView)values[0];
            int i = 0;
            auxiliaries.getItems()
               .forEach(
                  aux -> {
                     try {
                        AuxiliaryTableInfo auxiliary = (AuxiliaryTableInfo)aux;
                        if (auxiliary.isAssigned()) {
                           new PreparedSentence(
                                 ProductDataLogic.this.session,
                                 "insert into products_com (id, product, product2, siteguid) values (?, ?, ?, ?)  ",
                                 new SerializerWriteBasic(Datas.STRING, Datas.STRING, Datas.STRING, Datas.STRING)
                              )
                              .exec(new Object[]{UUID.randomUUID().toString(), auxiliary.getProduct(), auxiliary.getId(), auxiliary.getSiteguid()});
                        }
                     } catch (BasicException var3x) {
                        Logger.error((Throwable)var3x);
                     }
                  }
               );
            return i;
         }
      };
   }

   public void removeAuxiliaries(String product, String siteGuid) throws BasicException {
      new PreparedSentence(this.session, "delete from products_com where product = ? and siteguid = ? ", new SerializerWriteBasic(Datas.STRING, Datas.STRING))
         .exec(new Object[]{product, siteGuid});
   }

   public final String getUOMDescription(String id) {
      try {
         return (String)new StaticSentence(
               this.session, "select uomdescription from uom  where id = ? ", SerializerWriteString.INSTANCE, SerializerReadString.INSTANCE
            )
            .find(id);
      } catch (BasicException var3) {
         System.out.println("Error with uom");
         return null;
      }
   }

   public final Double getUomFactor(String id) {
      Double record = 0.0;

      try {
         record = (Double)new StaticSentence(
               this.session, "select uomfactor from uom where id = ? ", SerializerWriteString.INSTANCE, SerializerReadDouble.INSTANCE
            )
            .find(id);
      } catch (BasicException var4) {
         Logger.error((Throwable)var4);
      }

      return record == null ? 0.0 : record;
   }

   public Boolean barcodeInUse(String barcode) throws BasicException {
      Object record = new StaticSentence(
            this.session, "select count(*) from barcodes where barcode = ? ", SerializerWriteString.INSTANCE, SerializerReadInteger.INSTANCE
         )
         .find(barcode);
      return (Integer)record != 0;
   }

   public List<uk.chromis.administration.KeyedData> getIngredients(String id) {
      try {
         return new PreparedSentence(
               this.session,
               "select  id, concat(reference,  ' - ',  name) from products where isingredient = true  and (id != ?  and id not in (select ingredient from recipe_ingredients where  product = ?))  and retired = false order by name ",
               new SerializerWriteBasicExt(new Datas[]{Datas.STRING, Datas.STRING}, new int[]{0, 1}),
               uk.chromis.administration.KeyedData.getSerializerRead()
            )
            .list(new Object[]{id, id});
      } catch (BasicException var3) {
         Dialogs.errorAlert(var3, "Get Product list error");
         return null;
      }
   }

   public uk.chromis.administration.KeyedData getIngredient(String id) {
      try {
         return (uk.chromis.administration.KeyedData)new PreparedSentence(
               this.session,
               "select  id, concat(reference,  ' - ',  name) from products where id = ?  and retired = false",
               SerializerWriteString.INSTANCE,
               uk.chromis.administration.KeyedData.getSerializerRead()
            )
            .find(id);
      } catch (BasicException var3) {
         Dialogs.errorAlert(var3, "Get Product list error");
         return null;
      }
   }

   public List<uk.chromis.administration.KeyedData> getProductName(String productID, String categoryID) {
      if (productID.length() == 0) {
         return null;
      } else {
         if (categoryID != null && categoryID.length() != 0 && !categoryID.equals("**all**")) {
            try {
               return new PreparedSentence(
                     this.session,
                     "select  id, name from products where id != ?  and category = ?  and retired = false order by name ",
                     new SerializerWriteBasicExt(new Datas[]{Datas.STRING, Datas.STRING}, new int[]{0, 1}),
                     uk.chromis.administration.KeyedData.getSerializerRead()
                  )
                  .list(new Object[]{productID, categoryID});
            } catch (BasicException var5) {
               Dialogs.errorAlert(var5, "Get Product error");
            }
         } else {
            try {
               return new PreparedSentence(
                     this.session,
                     "select  id, name from products where id != ?  and retired = false order by name ",
                     SerializerWriteString.INSTANCE,
                     uk.chromis.administration.KeyedData.getSerializerRead()
                  )
                  .list(productID);
            } catch (BasicException var6) {
               Dialogs.errorAlert(var6, "Get Product error");
            }
         }

         return null;
      }
   }

   public void removeProduct(String id, String siteGUID) throws BasicException {
      new PreparedSentence(this.session, "delete from products where id ='" + id + "'  and siteguid ='" + siteGUID + "'", null).exec();
   }

   public void removeCategory(String id, String siteGUID) throws BasicException {
      new PreparedSentence(this.session, "delete from categories where id ='" + id + "'  and siteguid ='" + siteGUID + "'", null).exec();
   }

   public void removeStockCurrentIfNoStock(String productID, String siteGUID) throws BasicException {
      new PreparedSentence(this.session, "delete from stockcurrent where product ='" + productID + "'  and siteguid ='" + siteGUID + "' and units = 0 ", null)
         .exec();
   }

   public void removeProductSuppliers(String productID, String siteGUID) throws BasicException {
      new PreparedSentence(this.session, "delete from productsuppliers where productid ='" + productID + "'  and siteguid ='" + siteGUID + "'", null).exec();
   }

   public void removeProductBarcodes(String productID, String siteGUID) throws BasicException {
      new PreparedSentence(this.session, "delete from barcodes where productid ='" + productID + "'  and siteguid ='" + siteGUID + "'", null).exec();
   }

   public void removeTierPricing(String productID, String siteGUID) throws BasicException {
      new PreparedSentence(this.session, "delete from tierpricing where productid ='" + productID + "'  and siteguid ='" + siteGUID + "'", null).exec();
   }

   public void removeRecipeIngredients(String productID, String siteGUID) throws BasicException {
      new PreparedSentence(this.session, "delete from recipe_ingredients where product ='" + productID + "'  and siteguid ='" + siteGUID + "'", null).exec();
   }

   public final ProductInfo getProductInfo(String id) {
      try {
         return (ProductInfo)new PreparedSentence(
               this.session,
               "SELECT " + ProductData.getFieldList() + " from products where id = ? ",
               SerializerWriteString.INSTANCE,
               ProductInfo.getSerializerRead()
            )
            .find(id);
      } catch (BasicException var3) {
         Dialogs.errorAlert(var3, "Get ProductInfo error");
         return null;
      }
   }

   public List<ProductInfo> searchProduct(String searchStr) {
      try {
         return new StaticSentence(
               this.session,
               "select " + ProductData.getFieldList() + " from products  where CONCAT_WS(id, reference, code, name, alias) LIKE ? ",
               SerializerWriteString.INSTANCE,
               ProductInfo.getSerializerRead()
            )
            .list(searchStr);
      } catch (BasicException var3) {
         Dialogs.errorAlert(var3, "Get search Product error");
         return null;
      }
   }

   public List<ProductInfo> searchByValue(String searchStr) {
      try {
         return new StaticSentence(
               this.session,
               "select " + ProductData.getFieldList() + " from products  where " + searchStr + "  and retired = false",
               null,
               ProductInfo.getSerializerRead()
            )
            .list();
      } catch (BasicException var3) {
         Dialogs.errorAlert(var3, "Get search Product error");
         return null;
      }
   }

   public List<TierPricing> getTierPricing(String product, String siteguid) {
      final List<TierPricing> prices = new ArrayList<>();

      try {
         return new PreparedSentence(
               this.session,
               "select  tier1_qty, tier1_sellpriceinc, tier1_sellprice, tier2_qty, tier2_sellpriceinc, tier2_sellprice, tier3_qty, tier3_sellpriceinc, tier3_sellprice, tier4_qty, tier4_sellpriceinc, tier4_sellprice, tier5_qty, tier5_sellpriceinc, tier5_sellprice from tierpricing  where productid = ? and siteguid = ? ",
               new SerializerWriteBasicExt(new Datas[]{Datas.STRING, Datas.STRING}, new int[]{0, 1}),
               new SerializerRead() {
                  @Override
                  public Object readValues(DataRead dr) throws BasicException {
                     prices.add(new TierPricing(dr.getInt(1), dr.getDouble(2), dr.getDouble(3)));
                     prices.add(new TierPricing(dr.getInt(4), dr.getDouble(5), dr.getDouble(6)));
                     prices.add(new TierPricing(dr.getInt(7), dr.getDouble(8), dr.getDouble(9)));
                     prices.add(new TierPricing(dr.getInt(10), dr.getDouble(11), dr.getDouble(12)));
                     prices.add(new TierPricing(dr.getInt(13), dr.getDouble(14), dr.getDouble(15)));
                     return prices;
                  }
               }
            )
            .list(new Object[]{product, siteguid});
      } catch (BasicException var5) {
         System.out.println(var5);
         return null;
      }
   }

   public List<uk.chromis.administration.KeyedData> getProductFieldChecks(String reference, String code, String name, String id) {
      try {
         return this.productFieldChecks.list(new Object[]{reference, id, code, id, name, id});
      } catch (BasicException var6) {
         Dialogs.errorAlert(var6, "Get productFieldChecks Data error");
         return null;
      }
   }

   private String getProductFieldList(String prefix) {
      Iterator<String> i = ProductData.getProductTableInsert().iterator();
      StringBuilder fieldList = new StringBuilder();

      while (i.hasNext()) {
         if (prefix != null) {
            fieldList.append(prefix);
            fieldList.append(i.next());
         } else {
            fieldList.append(i.next());
         }
      }

      return fieldList.toString();
   }

   private String getProductFieldValues() {
      Iterator<String> i = ProductData.getProductTableInsert().iterator();
      StringBuilder fieldList = new StringBuilder();

      while (i.hasNext()) {
         fieldList.append("?,");
         i.next();
      }

      fieldList.deleteCharAt(fieldList.length() - 1);
      return fieldList.toString();
   }

   private String getProductFieldUpdate(String prefix) {
      Iterator<String> i = ProductData.getProductTableUpdate().iterator();
      StringBuilder fieldList = new StringBuilder();

      while (i.hasNext()) {
         if (prefix != null) {
            fieldList.append(prefix);
            fieldList.append(i.next());
         } else {
            fieldList.append(i.next());
         }
      }

      return fieldList.toString();
   }

   public Double getProductLocationStockLevel(String product, String location, String siteguid) {
      try {
         Double stock = (Double)new StaticSentence(
               this.session,
               " select units from stockcurrent  where product = ?  and location = ?  and siteguid = ? ",
               new SerializerWriteBasicExt(new Datas[]{Datas.STRING, Datas.STRING, Datas.STRING}, new int[]{0, 1, 2}),
               SerializerReadDouble.INSTANCE
            )
            .find(new Object[]{product, location, siteguid});
         return stock != null ? stock : 0.0;
      } catch (BasicException var5) {
         System.out.println("Error with siteguid");
         return 0.0;
      }
   }

   public Double getProductTotalStockLevel(String product, String siteguid) {
      try {
         Double stock = (Double)new StaticSentence(
               this.session,
               " select sum(units) from stockcurrent  where product = ?  and siteguid = ? ",
               new SerializerWriteBasicExt(new Datas[]{Datas.STRING, Datas.STRING}, new int[]{0, 1}),
               SerializerReadDouble.INSTANCE
            )
            .find(new Object[]{product, siteguid});
         return stock != null ? stock : 0.0;
      } catch (BasicException var4) {
         System.out.println("Error with siteguid");
         return 0.0;
      }
   }

   public Double getSuppliedUOMQty(String uom, String siteguid) {
      try {
         Double qty = (Double)new StaticSentence(
               this.session,
               " select uomfactor from uom  where id = ?  and siteguid = ? ",
               new SerializerWriteBasicExt(new Datas[]{Datas.STRING, Datas.STRING}, new int[]{0, 1}),
               SerializerReadDouble.INSTANCE
            )
            .find(new Object[]{uom, siteguid});
         return qty != null ? qty : 0.0;
      } catch (BasicException var4) {
         System.out.println("Error with uom factor");
         return 0.0;
      }
   }

   private String geUOMDescription(String uom, String siteguid) {
      try {
         return (String)new StaticSentence(
               this.session,
               " select uomfactor from uomdescription  where id = ?  and siteguid = ? ",
               new SerializerWriteBasicExt(new Datas[]{Datas.STRING, Datas.STRING}, new int[]{0, 1}),
               SerializerReadDouble.INSTANCE
            )
            .find(new Object[]{uom, siteguid});
      } catch (BasicException var4) {
         System.out.println("Error with uom description");
         return "";
      }
   }

   public final SentenceExec stockDiaryInsert() {
      return new SentenceExecTransaction(this.session) {
         @Override
         public int execInTransaction(Object values) throws BasicException {
            Object[] params = (Object[])values;
            int i = new PreparedSentence(
                  ProductDataLogic.this.session,
                  " insert into stockdiary (datenew, reason, location, product, units, cost, price, appuser, siteguid, supplier, reference) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                  new SerializerWriteBasicExt(
                     new Datas[]{
                        Datas.TIMESTAMP,
                        Datas.INT,
                        Datas.STRING,
                        Datas.STRING,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.DOUBLE,
                        Datas.STRING,
                        Datas.STRING,
                        Datas.STRING,
                        Datas.STRING
                     },
                     new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10}
                  )
               )
               .exec(params);
            Double openingStock = (Double)new StaticSentence(
                  ProductDataLogic.this.session,
                  " select ifnull(sum(units),0.0) from stockcurrent  where product = ? and siteguid = ? ",
                  new SerializerWriteBasicExt(new Datas[]{Datas.STRING, Datas.STRING}, new int[]{0, 1}),
                  SerializerReadDouble.INSTANCE
               )
               .find(new Object[]{(String)params[3], (String)params[8]});
            Double average = (Double)new PreparedSentence(
                  ProductDataLogic.this.session,
                  "select  (case when averagecost = 0.00  then pricebuy else averagecost end) as result  from products where id = ? ",
                  SerializerWriteString.INSTANCE,
                  SerializerReadDouble.INSTANCE
               )
               .find((String)params[3]);
            Double newAverage = (openingStock * average + (Double)params[4] * (Double)params[5]) / (openingStock + (Double)params[4]);
            if ((Integer)params[1] == 1 || (Integer)params[1] == 1000) {
               int var7 = new StaticSentence(
                     ProductDataLogic.this.session,
                     "update products set averagecost = ?, pricebuy = ?  where id = ? ",
                     new SerializerWriteBasic(Datas.DOUBLE, Datas.DOUBLE, Datas.STRING)
                  )
                  .exec(new Object[]{newAverage, (Double)params[5], (String)params[3]});
            }

            params[5] = (Double)new StaticSentence(
                  ProductDataLogic.this.session,
                  " select ifnull(units,0.0) + ?  from stockcurrent  where location = ? and product = ? and siteguid = ?",
                  new SerializerWriteBasicExt(new Datas[]{Datas.DOUBLE, Datas.STRING, Datas.STRING, Datas.STRING}, new int[]{0, 1, 2, 3}),
                  SerializerReadDouble.INSTANCE
               )
               .find(new Object[]{params[4], params[2], params[3], params[8]});
            new PreparedSentence(
                  ProductDataLogic.this.session,
                  "replace into stockcurrent (location, product, units, siteguid) values (?, ?, ?, ?)",
                  new SerializerWriteBasicExt(ProductDataLogic.this.stockAdjustDatas, new int[]{0, 1, 2, 3})
               )
               .exec(new Object[]{params[2], params[3], params[5], params[8]});
            return i;
         }
      };
   }

   private int[] getProductIndexInsert() {
      return new int[]{
         0,
         1,
         2,
         3,
         4,
         5,
         6,
         7,
         8,
         9,
         10,
         11,
         12,
         13,
         14,
         15,
         16,
         17,
         18,
         19,
         20,
         21,
         22,
         23,
         24,
         25,
         26,
         27,
         28,
         29,
         30,
         31,
         32,
         33,
         34,
         35,
         36,
         37,
         38,
         39,
         40,
         41,
         42,
         43,
         44,
         45,
         46
      };
   }

   private int[] getProductIndexUpdate() {
      return new int[]{
         1,
         2,
         3,
         4,
         5,
         6,
         7,
         8,
         9,
         10,
         11,
         12,
         13,
         14,
         15,
         16,
         17,
         18,
         19,
         20,
         21,
         22,
         23,
         24,
         25,
         26,
         27,
         28,
         29,
         31,
         32,
         33,
         34,
         35,
         36,
         37,
         38,
         39,
         40,
         41,
         43,
         44,
         45,
         0,
         46
      };
   }
}
