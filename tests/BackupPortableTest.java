import uk.chromis.pos.backup.DatabaseDumper;

/**
 * El respaldo debe poder restaurarse en una base con otro nombre: MariaDB
 * 10.6 escribe el nombre de la base dentro de la vista "recipes"
 * (`base_pos`.`recipe_ingredients`.`product`) y al restaurar en
 * "base_pos_verif" fallaba con "Unknown column".
 *
 *   java -cp dist/chromispos.jar tests/BackupPortableTest.java
 */
public class BackupPortableTest {
    static int fails = 0;
    static void check(String what, Object got, Object exp) {
        boolean ok = java.util.Objects.equals(got, exp);
        System.out.println((ok ? "OK   " : "FAIL ") + what + " -> " + got);
        if (!ok) fails++;
    }
    public static void main(String[] a) {
        check("quita el nombre de la base",
                DatabaseDumper.portable("select distinct `base_pos`.`recipe_ingredients`.`product` from `base_pos`.`recipe_ingredients`", "base_pos"),
                "select distinct `recipe_ingredients`.`product` from `recipe_ingredients`");
        check("no toca otras bases", DatabaseDumper.portable("select `otra`.`t`.`c` from `otra`.`t`", "base_pos"),
                "select `otra`.`t`.`c` from `otra`.`t`");
        check("no toca nombres parecidos", DatabaseDumper.portable("select `base_pos2`.`t`.`c`", "base_pos"), "select `base_pos2`.`t`.`c`");
        check("nulo", DatabaseDumper.portable(null, "base_pos"), null);
        System.out.println(fails == 0 ? "TODO BIEN" : fails + " FALLAS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
